/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.langlib.table;

import org.ballerinalang.jvm.scheduling.Strand;
import org.ballerinalang.jvm.values.FPValue;
import org.ballerinalang.jvm.values.TableValueImpl;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.natives.annotations.Argument;
import org.ballerinalang.natives.annotations.BallerinaFunction;

import java.util.Collection;
import java.util.Iterator;

/**
 * Native implementation of lang.table:forEach(table&lt;Type&gt;, function).
 *
 */
@BallerinaFunction(
        orgName = "ballerina", packageName = "lang.table", functionName = "forEach",
        args = {@Argument(name = "tbl", type = TypeKind.TABLE), @Argument(name = "func", type = TypeKind.FUNCTION)},
        isPublic = true
)
public class Foreach {
    public static void forEach(Strand strand, TableValueImpl tbl, FPValue<Object, Object> func) {
        Collection collection = tbl.values();
        Iterator iterator = collection.iterator();
        while (iterator.hasNext()) {
            func.call(new Object[]{strand, iterator.next(), true});
        }
    }
}