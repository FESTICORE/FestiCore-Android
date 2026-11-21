package io.festicore.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import org.robolectric.RuntimeEnvironment

/** 같은 파일을 쓰는 DataStore를 열고 닫는다. 같은 파일에 DataStore 두 개를 동시에 열 수 없어 닫은 뒤 다시 연다. */
internal class TestDataStores(private val name: String) {
    private var job: Job? = null

    fun open(): DataStore<Preferences> {
        val newJob = Job()
        job = newJob
        return PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + newJob)) {
            RuntimeEnvironment.getApplication().preferencesDataStoreFile(name)
        }
    }

    suspend fun close() {
        job?.cancelAndJoin()
        job = null
    }
}
