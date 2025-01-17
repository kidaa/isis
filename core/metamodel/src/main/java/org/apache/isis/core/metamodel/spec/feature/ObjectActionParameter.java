/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.core.metamodel.spec.feature;

import com.google.common.base.Function;

import org.apache.isis.applib.filter.Filter;
import org.apache.isis.applib.profiles.Localization;
import org.apache.isis.core.commons.authentication.AuthenticationSession;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.consent.InteractionInvocationMethod;
import org.apache.isis.core.metamodel.deployment.DeploymentCategory;
import org.apache.isis.core.metamodel.facets.all.named.NamedFacet;
import org.apache.isis.core.metamodel.interactions.ActionArgumentContext;

/**
 * Analogous to {@link ObjectAssociation}.
 */
public interface ObjectActionParameter extends ObjectFeature, CurrentHolder {

    /**
     * If true then can cast to a {@link OneToOneActionParameter}.
     * 
     * <p>
     * Either this or {@link #isCollection()} will be true.
     * 
     * <p>
     * Design note: modelled after {@link ObjectAssociation#isNotCollection()}
     */
    boolean isObject();

    /**
     * Only for symmetry with {@link ObjectAssociation}, however since the NOF
     * does not support collections as actions all implementations should return
     * <tt>false</tt>.
     */
    boolean isCollection();

    /**
     * Owning {@link ObjectAction}.
     */
    ObjectAction getAction();

    /**
     * Returns a flag indicating if it can be left unset when the action can be
     * invoked.
     */
    boolean isOptional();

    /**
     * Returns the 0-based index to this parameter.
     */
    int getNumber();

    /**
     * Returns the name of this parameter.
     * 
     * <p>
     * Because Java's reflection API does not allow us to access the code name
     * of the parameter, we have to do figure out the name of the parameter
     * ourselves:
     * <ul>
     * <li>If there is a {@link NamedFacet} associated with this parameter then
     * we infer a name from this, eg "First Name" becomes "firstName".
     * <li>Otherwise we use the type, eg "string".
     * <li>If there is more than one parameter of the same type, then we use a
     * numeric suffix (eg "string1", "string2"). Wrappers and primitives are
     * considered to be the same type.
     * </ul>
     */
    @Override
    String getName();

    ActionArgumentContext createProposedArgumentInteractionContext(AuthenticationSession session, InteractionInvocationMethod invocationMethod, ObjectAdapter targetObject, ObjectAdapter[] args, int position);


    /**
     * Whether there is an autoComplete provided (eg <tt>autoCompleteXxx</tt> supporting
     * method) for the parameter.
     */
    boolean hasAutoComplete();
    
    /**
     * Returns a list of possible references/values for this parameter, which the
     * user can choose from, based on the input search argument.
     */
    ObjectAdapter[] getAutoComplete(
            final ObjectAdapter adapter,
            final String searchArg,
            final AuthenticationSession authenticationSession,
            final DeploymentCategory deploymentCategory);

    
    
    int getAutoCompleteMinLength();
    /**
     * Whether there are any choices provided (eg <tt>choicesXxx</tt> supporting
     * method) for the parameter.
     */
    boolean hasChoices();

    /**
     * Returns a list of possible references/values for this parameter, which the
     * user can choose from.
     */
    ObjectAdapter[] getChoices(
            final ObjectAdapter adapter,
            final ObjectAdapter[] argumentsIfAvailable,
            final AuthenticationSession authenticationSession,
            final DeploymentCategory deploymentCategory);


    ObjectAdapter getDefault(ObjectAdapter adapter);

    
    /**
     * Whether proposed value for this parameter is valid.
     * 
     * @param adapter
     * @param proposedValue
     * @return
     */
    String isValid(ObjectAdapter adapter, Object proposedValue, Localization localization);
 

    
    public static class Filters {
        private Filters(){}
        
        /**
         * Filters only parameters that are for objects (ie 1:1 associations)
         */
        public static final Filter<ObjectActionParameter> PARAMETER_ASSOCIATIONS = new Filter<ObjectActionParameter>() {
            @Override
            public boolean accept(final ObjectActionParameter parameter) {
                return parameter.getSpecification().isNotCollection();
            }
        };
    }
    public static class Functions {
        public static final Function<ObjectActionParameter, String> GET_NAME = new Function<ObjectActionParameter, String>() {
            @Override public String apply(final ObjectActionParameter input) {
                return input.getName();
            }
        };
        public static final Function<ObjectActionParameter, Class<?>> GET_TYPE = new Function<ObjectActionParameter, Class<?>>() {
            @Override public Class<?> apply(final ObjectActionParameter input) {
                return input.getSpecification().getCorrespondingClass();
            }
        };

        private Functions(){}

    }
}
