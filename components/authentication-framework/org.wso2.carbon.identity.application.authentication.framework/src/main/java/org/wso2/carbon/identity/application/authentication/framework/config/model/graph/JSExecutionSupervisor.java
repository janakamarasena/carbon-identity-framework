/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.carbon.identity.application.authentication.framework.config.model.graph;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Supervises the execution of any script engine, and kills the thread if the time taken is too much.
 */
public class JSExecutionSupervisor {

    private static final Log LOG = LogFactory.getLog(JSExecutionSupervisor.class);
    private static final String JS_EXECUTION_MONITOR = "JS-Exec-Monitor";
    private final long TIMEOUT_IN_MILLIS;
    private final int THREAD_COUNT;
    private long taskExecutionRateInMillis = 100L;
    private Map<String, ScheduledFuture> currentScriptExecutions = new HashMap<>();
    private Map<String, MonitoringTask> monitoredTasks = new HashMap<>();
    private ScheduledExecutorService monitoringService;

    public JSExecutionSupervisor(int threadCount, long timeoutInMillis) {

        this.THREAD_COUNT = threadCount;
        this.TIMEOUT_IN_MILLIS = timeoutInMillis;

        if (taskExecutionRateInMillis > timeoutInMillis) {
            taskExecutionRateInMillis = timeoutInMillis;
        }

        init();
    }

    private void init() {

        monitoringService = new ScheduledThreadPoolExecutor(THREAD_COUNT, r -> new Thread(r, JS_EXECUTION_MONITOR));
    }

    /**
     * Shutdown the execution supervisor.
     */
    public void shutdown() {

        monitoringService.shutdown();
    }

    /**
     * Start monitoring an adaptive auth execution.
     *
     * @param identifier Monitoring task identifier.
     * @param serviceProvider Service provider of the adaptive auth script.
     * @param tenantDomain Tenant domain.
     * @param elapsedTimeInMillis Elapsed time in milliseconds if a previous
     *                            adaptive auth execution happened in the same thread.
     */
    public void monitor(String identifier, String serviceProvider, String tenantDomain, long elapsedTimeInMillis) {

        MonitoringTask monitoringTask = new MonitoringTask(Thread.currentThread(), identifier, serviceProvider,
                tenantDomain, elapsedTimeInMillis);
        monitoredTasks.put(identifier, monitoringTask);
        ScheduledFuture<?> monitoringFuture = monitoringService.
                scheduleAtFixedRate(monitoringTask, taskExecutionRateInMillis, taskExecutionRateInMillis,
                        TimeUnit.MILLISECONDS);

        currentScriptExecutions.put(identifier, monitoringFuture);
    }

    /**
     * Mark adaptive auth script execution as complete and stop monitoring.
     *
     * @param identifier Monitoring task identifier.
     * @return Total elapsed time of adaptive auth execution in the current thread.
     */
    public long competed(String identifier) {

        MonitoringTask task = monitoredTasks.remove(identifier);
        ScheduledFuture<?> monitoredFuture = currentScriptExecutions.remove(identifier);
        if (monitoredFuture == null) {
            // Nothing to be done as there was no such task with the given identifier.
            return 0L;
        }
        monitoredFuture.cancel(false);

        if (task == null) {
            return 0L;
        }
        return task.getTotalElapsedTime();
    }

    private class MonitoringTask implements Runnable {

        private Thread originalThread;
        private long timeCreated;
        private long elapsedTimeInMillis;
        private String id;
        private String serviceProvider;
        private String tenantDomain;

        public MonitoringTask(Thread originalThread, String id, String serviceProvider, String tenantDomain,
                              long elapsedTimeInMillis) {

            this.originalThread = originalThread;
            this.id = id;
            this.serviceProvider = serviceProvider;
            this.tenantDomain = tenantDomain;
            this.timeCreated = System.currentTimeMillis();
            this.elapsedTimeInMillis = elapsedTimeInMillis;
        }

        @Override
        public void run() {

            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("JS execution monitoring task running. Thread: %s, service " +
                "provider: %s, tenant: %s.", originalThread.getName(), serviceProvider, tenantDomain));
            }

            long elapsedTime  = getTotalElapsedTime();
            if(elapsedTime > TIMEOUT_IN_MILLIS) {
                StackTraceElement[] stackTraceElements = originalThread.getStackTrace();
                Throwable throwable = new Throwable();
                throwable.setStackTrace(stackTraceElements);
                LOG.warn(String.format("The script took too much time to execute. Thread: %s, service " +
                        "provider: %s, tenant: %s, execution duration: %s(ms).", originalThread.getName(),
                        serviceProvider, tenantDomain, elapsedTime), throwable);
                originalThread.interrupt();
                originalThread.stop();

                // Marking current monitoring task as complete.
                competed(id);
            }
        }

        private long getTotalElapsedTime() {

            return (System.currentTimeMillis() - timeCreated) + elapsedTimeInMillis;
        }
    }
}
