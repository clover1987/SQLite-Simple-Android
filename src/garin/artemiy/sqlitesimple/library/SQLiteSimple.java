package garin.artemiy.sqlitesimple.library;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import garin.artemiy.sqlitesimple.library.annotations.Column;
import garin.artemiy.sqlitesimple.library.util.SimpleConstants;
import garin.artemiy.sqlitesimple.library.util.SimpleDatabaseUtil;
import garin.artemiy.sqlitesimple.library.util.SimplePreferencesUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * author: Artemiy Garin
 * Copyright (C) 2013 SQLite Simple Project
 * *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * *
 * http://www.apache.org/licenses/LICENSE-2.0
 * *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class SQLiteSimple {

    private SQLiteSimpleHelper sqLiteSimpleHelper;
    private SimplePreferencesUtil sharedPreferencesUtil;
    private int databaseVersion;

    @SuppressWarnings("unused")
    public SQLiteSimple(Context context, int databaseVersion) {
        sharedPreferencesUtil = new SimplePreferencesUtil(context);
        this.databaseVersion = databaseVersion;
        commitDatabaseVersion();
        sqLiteSimpleHelper = new SQLiteSimpleHelper(context, databaseVersion, null);
    }

    @SuppressWarnings("unused")
    public SQLiteSimple(Context context) {
        sharedPreferencesUtil = new SimplePreferencesUtil(context);
        this.databaseVersion = SimpleConstants.FIRST_DATABASE_VERSION;

        commitDatabaseVersion();

        sqLiteSimpleHelper = new SQLiteSimpleHelper(context, databaseVersion, null);
    }

    @SuppressWarnings("unused")
    public SQLiteSimple(Context context, String localDatabaseName) {
        sharedPreferencesUtil = new SimplePreferencesUtil(context);
        this.databaseVersion = SimpleConstants.FIRST_DATABASE_VERSION;

        commitDatabaseVersion();

        sqLiteSimpleHelper = new SQLiteSimpleHelper(context, databaseVersion, localDatabaseName);
    }

    private void commitDatabaseVersion() {
        if (databaseVersion > sharedPreferencesUtil.getDatabaseVersion()) {
            sharedPreferencesUtil.putDatabaseVersion(databaseVersion);
            sharedPreferencesUtil.commit();
        }
    }

    private String makeFormatForColumnsTwoArguments(int index, Class classEntity) {
        String format;
        if (index == 0) {
            format = SimpleConstants.SQL_QUERY_APPEND_FORMAT_TWO_ARGUMENTS_FIRST;
        } else if (index == classEntity.getDeclaredFields().length - 1) { // check need default or ");" ending
            format = SimpleConstants.SQL_QUERY_APPEND_FORMAT_TWO_ARGUMENTS_LAST;
        } else {
            format = SimpleConstants.SQL_QUERY_APPEND_FORMAT_TWO_ARGUMENTS;
        }
        return format;
    }

    private void commit(List<String> tables, List<String> sqlQueries) {
        sharedPreferencesUtil.putList(SimpleConstants.SHARED_DATABASE_TABLES, tables);
        sharedPreferencesUtil.putList(SimpleConstants.SHARED_DATABASE_QUERIES, sqlQueries);
        sharedPreferencesUtil.commit();
    }

    private void checkingCommit(List<String> tables, List<String> sqlQueries, boolean newDatabaseVersion) {
        if (newDatabaseVersion) {
            commit(tables, sqlQueries);
            SQLiteDatabase sqliteDatabase = sqLiteSimpleHelper.getWritableDatabase(); // call onCreate();
            sqliteDatabase.close();
        } else {
            commit(tables, sqlQueries);
        }
    }

    public void create(Class<?>... classes) {

        List<String> savedTables = sharedPreferencesUtil.getList(SimpleConstants.SHARED_DATABASE_TABLES);
        List<String> savedSQLQueries = sharedPreferencesUtil.getList(SimpleConstants.SHARED_DATABASE_QUERIES);
        sharedPreferencesUtil.clearAllPreferences(databaseVersion);
        List<String> tables = new ArrayList<String>();
        List<String> sqlQueries = new ArrayList<String>();

        for (Class classEntity : classes) {
            StringBuilder sqlQueryBuilder = new StringBuilder();
            String table = SimpleDatabaseUtil.getTableName(classEntity);
            sqlQueryBuilder.append(String.format(SimpleConstants.CREATE_TABLE_IF_NOT_EXIST, table));

            for (int i = 0; i < classEntity.getDeclaredFields().length; i++) {
                Field fieldEntity = classEntity.getDeclaredFields()[i];
                Column fieldEntityAnnotation = fieldEntity.getAnnotation(Column.class);
                if (fieldEntityAnnotation != null) { // if field what we need annotated
                    String column = SimpleDatabaseUtil.getColumnName(fieldEntity);
                    String format = makeFormatForColumnsTwoArguments(i, classEntity);
                    sqlQueryBuilder.append(String.format(format, column, fieldEntityAnnotation.type()));

                    if (fieldEntityAnnotation.isPrimaryKey()) {
                        sqlQueryBuilder.append(SimpleConstants.SPACE);
                        sqlQueryBuilder.append(SimpleConstants.PRIMARY_KEY);
                    }
                    if (fieldEntityAnnotation.isAutoincrement()) {
                        sqlQueryBuilder.append(SimpleConstants.SPACE);
                        sqlQueryBuilder.append(SimpleConstants.AUTOINCREMENT);
                    }

                }
            }

            tables.add(table);
            sqlQueries.add(sqlQueryBuilder.toString());
        }

        boolean newDatabaseVersion = false;
        if (databaseVersion > sharedPreferencesUtil.getDatabaseVersion())
            newDatabaseVersion = true;

        boolean isRebasedTables = false;
        if (!newDatabaseVersion) {
            isRebasedTables = rebaseTablesIfNeed(savedTables, tables, sqlQueries, savedSQLQueries);
            if (savedSQLQueries.hashCode() != sqlQueries.hashCode() && savedSQLQueries.hashCode() != 1) {
                addNewColumnsIfNeed(tables, sqlQueries, savedSQLQueries);
            }
        }
        if (!isRebasedTables) {
            checkingCommit(tables, sqlQueries, newDatabaseVersion);
        }
    }

    // Delete extra tables
    private boolean rebaseTablesIfNeed(List<String> savedTables, List<String> tables,
                                       List<String> sqlQueries, List<String> savedSQLQueries) {

        List<String> extraTables = new ArrayList<String>(savedTables); // possible extra tables
        extraTables.removeAll(tables);

        if (extraTables.size() != 0) { // drop tables
            List<String> extraSqlQueries = new ArrayList<String>(savedSQLQueries); // extra SQL queries
            extraSqlQueries.removeAll(sqlQueries);

            SQLiteDatabase sqLiteDatabase = sqLiteSimpleHelper.getWritableDatabase();
            for (String extraTable : extraTables) {
                sqLiteDatabase.execSQL(String.format(SimpleConstants.FORMAT_GLUED,
                        SimpleConstants.DROP_TABLE_IF_EXISTS, extraTable));
            }

            commit(tables, sqlQueries);
            sqLiteSimpleHelper.onCreate(sqLiteDatabase);
            sqLiteDatabase.close();
            return true;
        }

        List<String> tablesToCreate = new ArrayList<String>(tables); // possible tables to need create
        tablesToCreate.removeAll(savedTables);

        if (tablesToCreate.size() != 0) {
            List<String> sqlQueriesToCreate = new ArrayList<String>(sqlQueries); // extra SQL queries
            sqlQueriesToCreate.removeAll(savedSQLQueries);

            SQLiteDatabase sqLiteDatabase = sqLiteSimpleHelper.getWritableDatabase();
            for (String sqlQuery : sqlQueriesToCreate) {
                sqLiteDatabase.execSQL(sqlQuery);
            }
        }

        return false;
    }

    // add columns to table if need
    private boolean addNewColumnsIfNeed(List<String> tables, List<String> sqlQueries, List<String> savedSqlQueries) {
        try {
            // if change name of column or add new column, or delete
            boolean isAddNewColumn = false;
            for (int i = 0; i < tables.size(); i++) {
                String table = tables.get(i);
                for (String savedSqlQuery : savedSqlQueries) {
                    if (table.contains(savedSqlQuery)) {

                        List<String> savedColumns = Arrays.asList(savedSqlQueries.get(i).
                                replace(String.format(SimpleConstants.CREATE_TABLE_IF_NOT_EXIST, table), SimpleConstants.EMPTY).
                                replace(SimpleConstants.LAST_BRACKET, SimpleConstants.EMPTY).
                                split(SimpleConstants.DIVIDER));

                        List<String> columns = Arrays.asList(sqlQueries.get(i).
                                replace(String.format(SimpleConstants.CREATE_TABLE_IF_NOT_EXIST, table), SimpleConstants.EMPTY).
                                replace(SimpleConstants.LAST_BRACKET, SimpleConstants.EMPTY).
                                split(SimpleConstants.DIVIDER));

                        if (columns.size() > savedColumns.size()) {

                            List<String> extraColumns = new ArrayList<String>(columns);
                            extraColumns.removeAll(savedColumns);

                            SQLiteDatabase sqLiteDatabase = sqLiteSimpleHelper.getWritableDatabase();
                            for (String column : extraColumns) {
                                sqLiteDatabase.execSQL(String.format(SimpleConstants.ALTER_TABLE_ADD_COLUMN, table, column));
                            }
                            sqLiteDatabase.close();
                            isAddNewColumn = true;
                        }
                    }
                }
            }
            return isAddNewColumn;
        } catch (IndexOutOfBoundsException exception) {
            // duplicated class on databaseSimple.create(...);
            exception.printStackTrace();
            return false;
        }
    }

}