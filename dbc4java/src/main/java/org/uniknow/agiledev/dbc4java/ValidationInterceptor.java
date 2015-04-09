/**
 * Copyright (C) 2014 uniknow. All rights reserved.
 * 
 * This Java class is subject of the following restrictions:
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. The end-user documentation included with the redistribution, if any, must
 * include the following acknowledgment: "This product includes software
 * developed by uniknow." Alternately, this acknowledgment may appear in the
 * software itself, if and wherever such third-party acknowledgments normally
 * appear.
 * 
 * 4. The name ''uniknow'' must not be used to endorse or promote products
 * derived from this software without prior written permission.
 * 
 * 5. Products derived from this software may not be called ''UniKnow'', nor may
 * ''uniknow'' appear in their name, without prior written permission of
 * uniknow.
 * 
 * THIS SOFTWARE IS PROVIDED ''AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL WWS OR ITS
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.uniknow.agiledev.dbc4java;

import java.util.HashSet;
import java.util.Set;

import javax.validation.*;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.validator.method.MethodConstraintViolation;
import org.hibernate.validator.method.MethodConstraintViolationException;
import org.hibernate.validator.method.MethodValidator;

/**
 * Intercepts method calls of calsses which are annotated with
 * {@code @Validated}.
 * 
 * @author mase
 * @since 0.1.3
 */
@Aspect
public class ValidationInterceptor {

    private Validator validator;

    public ValidationInterceptor() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Matches constructor in class annotated with `@Validated`.
     * <p/>
     * *NOTE:* This will only work when class compiled with aspectj.
     */
    @After("execution((@org.uniknow.agiledev.dbc4java.Validated *).new(..))")
    public void validateConstructorInvocation(JoinPoint joinPoint)
        throws Throwable {
        // Validate invariants class
        Set<ConstraintViolation<Object>> violations = validator
            .validate(joinPoint.getTarget());
        if (!violations.isEmpty()) {
            System.out.println(violations);
            throw new ConstraintViolationException(
                new HashSet<ConstraintViolation<?>>(violations));
        }
    }

    /**
     * Matches any public method, beside equals and hashcode, in a class
     * annotated with `@Validated`.
     */
    @Around("execution(public * (@org.uniknow.agiledev.dbc4java.Validated *).*(..)) && !execution( * *.equals(..)) && !execution(* *.hashCode(..))")
    public Object validateMethodInvocation(ProceedingJoinPoint pjp)
        throws Throwable {

        Object result;
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        MethodValidator methodValidator = validator
            .unwrap(MethodValidator.class);
        Object instance = pjp.getTarget();

        // Only validate constaints on object instances
        if (instance != null) {

            // Validate constraint(s) method parameters.
            Set<MethodConstraintViolation<Object>> parametersViolations = methodValidator
                .validateAllParameters(pjp.getTarget(), signature.getMethod(),
                    pjp.getArgs());
            if (!parametersViolations.isEmpty()) {
                throw new MethodConstraintViolationException(
                    parametersViolations);
            }

            result = pjp.proceed(); // Execute the method

            Set<ConstraintViolation<Object>> violations;

            // Validate invariants class
            violations = validator.validate(pjp.getTarget());
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(
                    new HashSet<ConstraintViolation<?>>(violations));
            }

            // Validate constraint return value method
            Set<MethodConstraintViolation<Object>> returnValueViolations = methodValidator
                .validateReturnValue(pjp.getTarget(), signature.getMethod(),
                    result);
            if (!returnValueViolations.isEmpty()) {
                throw new MethodConstraintViolationException(
                    returnValueViolations);
            }
        } else {
            result = pjp.proceed(); // Execute the method
        }

        return result;
    }

}
