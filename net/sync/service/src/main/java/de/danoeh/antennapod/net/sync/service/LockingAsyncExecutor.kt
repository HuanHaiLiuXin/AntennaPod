package de.danoeh.antennapod.net.sync.service

import java.util.concurrent.locks.ReentrantLock

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers

class LockingAsyncExecutor {

    companion object {
        private val lock = ReentrantLock()

        /**
         * Take the lock and execute runnable (to prevent changes to preferences being lost when enqueueing while sync is
         * in progress). If the lock is free, the runnable is directly executed in the calling thread to prevent overhead.
         */
        @JvmStatic
        fun executeLockedAsync(runnable: Runnable) {
            if (lock.tryLock()) {
                try {
                    runnable.run()
                } finally {
                    lock.unlock()
                }
            } else {
                Completable.fromRunnable {
                    lock.lock()
                    try {
                        runnable.run()
                    } finally {
                        lock.unlock()
                    }
                }.subscribeOn(Schedulers.computation())
                        .subscribe()
            }
        }

        @JvmStatic
        fun unlock() {
            lock.unlock()
        }

        @JvmStatic
        fun lock() {
            lock.lock()
        }
    }
}
