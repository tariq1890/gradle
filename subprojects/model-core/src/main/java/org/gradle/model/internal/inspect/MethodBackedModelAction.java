/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.internal.inspect;

import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;

class MethodBackedModelAction<T> extends AbstractModelActionWithView<T> {
    private final ModelRuleInvoker<?> ruleInvoker;

    public MethodBackedModelAction(MethodRuleDefinition<?, T> ruleDefinition) {
        this(ruleDefinition.getRuleInvoker(), ruleDefinition.getDescriptor(), ruleDefinition.getSubjectReference(), ruleDefinition.getTailReferences());
    }

    public MethodBackedModelAction(ModelRuleInvoker<?> ruleInvoker, ModelRuleDescriptor descriptor, ModelReference<T> subject, List<ModelReference<?>> inputs) {
        super(subject, descriptor, inputs);
        this.ruleInvoker = ruleInvoker;
    }

    @Override
    protected void execute(MutableModelNode modelNode, T view, List<ModelView<?>> inputs) {
        Object[] args = new Object[1 + this.inputs.size()];
        args[0] = view;
        for (int i = 0; i < this.inputs.size(); ++i) {
            args[i + 1] = inputs.get(i).getInstance();
        }
        ruleInvoker.invoke(args);
    }

    @Override
    public String toString() {
        return "MethodBackedModelAction{descriptor=" + descriptor + ", subject=" + subject + ", inputs=" + inputs + '}';
    }
}
