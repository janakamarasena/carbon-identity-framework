/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.com).
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
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

package org.wso2.carbon.identity.application.authentication.framework.config.model.graph.supervisor;

import org.wso2.carbon.identity.application.authentication.framework.exception.FrameworkException;
import org.wso2.carbon.identity.application.authentication.framework.internal.FrameworkServiceDataHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Wrapper class to wrap adaptive auth functions that should be excluded from monitoring.
 */
public class SupervisorExclusionFnWrapper implements InvocationWrapperFunction {

    private final String functionName;
    private final Object function;
    private final String monitorId;

    public SupervisorExclusionFnWrapper(String functionName, Object function, String monitorId) {

        this.functionName = functionName;
        this.function = function;
        this.monitorId = monitorId;
    }

    @Override
    public Object invoke(Object... args) throws FrameworkException {

        try {
            JSExecutionMonitorData scriptExecutionData = stopScriptExecutionMonitor();

            Method fnMethod = getFnMethod(function, functionName);
            Object response = fnMethod.invoke(function, args);

            /* In case of an exception the execution flow will stop
             an there will be a authentication error. Therefor there
             is no point in starting the script monitor again if
             there is an exception. Hence not putting it in a finally
             block.*/
            startScriptExecutionMonitor(scriptExecutionData);
            return response;
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new FrameworkException("Exception occurred while invoking function: " + functionName + " from class: "
                    + function.getClass(), e);
        }
    }

    private Method getFnMethod(Object function, String functionName) throws FrameworkException {

        Method[] methods =  function.getClass().getDeclaredMethods();
        Method fnMethod = null;
        for (Method method : methods) {
            if (method.getName().equals(functionName)) {
                fnMethod = method;
            }
        }
        if (fnMethod == null) {
            throw new FrameworkException("Unable to find method: " + functionName + " in class: "
                    + function.getClass());
        }
        if (!fnMethod.isAccessible()) {
            /* Need to set accessible true for adaptive
             auth functions registered as lambda
             functions to invoke through reflection. */
            fnMethod.setAccessible(true);
        }
        return fnMethod;
    }

    private JSExecutionMonitorData stopScriptExecutionMonitor() {

        return getJSExecutionSupervisor().completed(monitorId);
    }

    private void startScriptExecutionMonitor(JSExecutionMonitorData scriptExecutionData) {

        getJSExecutionSupervisor().monitor(monitorId, scriptExecutionData);
    }

    private JSExecutionSupervisor getJSExecutionSupervisor() {

        return FrameworkServiceDataHolder.getInstance().getJsExecutionSupervisor();
    }
}
