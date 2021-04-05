package org.ebur.debitum.database;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PersonRepository {

    private PersonDao personDao;

    private LiveData<List<Person>> allPersons;

    // Note that in order to unit test the Repository, you have to remove the Application
    // dependency. This adds complexity and much more code, and this sample is not about testing.
    // See the BasicSample in the android-architecture-components repository at
    // https://github.com/googlesamples
    public PersonRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        personDao = db.personDao();
        allPersons = personDao.getAllPersons();
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    public LiveData<List<Person>> getAllPersons() { return allPersons; }

    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    public void insert(Person person) {
        AppDatabase.databaseTaskExecutor.execute(() -> {
            personDao.insert(person);
        });
    }

    public int getPersonId(String name) throws ExecutionException, InterruptedException {
        Future<Integer> future = AppDatabase.databaseTaskExecutor.submit( () -> personDao.getPersonId(name));
        return future.get();
    }

    public boolean exists(String name) throws ExecutionException, InterruptedException {
        Future<Boolean> future = AppDatabase.databaseTaskExecutor.submit( () -> personDao.exists(name));
        return future.get();
    }
}