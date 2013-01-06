**Status - in the development**

<h2>Install</h2>

<h2>Usage</h2>

- Create class extends Application.

```java
public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SQLiteDatabaseSimple databaseSimple = new SQLiteDatabaseSimple(this);
        databaseSimple.create(Record.class);
    }

}
```

- Add to **AndroidManifest.xml**

```java
    <application
        android:name=".MainApplication"
        ...
        ...
            >
```

<h2>Notices</h2>
**Database version** - if upgrade database version, for example from 1 to 2, your all tables will be deleted, and created again. **DATA WILL BE LOST.**
If you want only add column, just write it on model and SQLite Simple create it for you.

<h3>Be careful</h3>

Be careful with attribute «notNull», because at some point you may **delete column** from model with this attribute and got error:
```java
    Error inserting ...
    android.database.sqlite.SQLiteConstraintException: error code 19: constraint failed
```
It means the column, what you delete - null, and SQLite can not add new row!