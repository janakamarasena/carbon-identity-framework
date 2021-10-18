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

/**
 * Function definition for a wrapper function that can be used to invoke another function.
 */
@FunctionalInterface
public interface InvocationWrapperFunction {

    /**
     * Used to invoke a wrapped function.
     *
     * @param args Arguments to be passed to the wrapped function.
     * @return Returns the result from the wrapped function.
     * @throws FrameworkException Thrown if there is an error
     *                            while invoking the wrapped function or when the wrapped
     *                            function itself throws an error.
     */
    Object invoke(Object... args) throws FrameworkException;
}
