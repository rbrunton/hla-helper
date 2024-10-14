/*
 * © 2024  The Johns Hopkins University Applied Physics Laboratory LLC.
 */

package edu.jhuapl.hlahelper.framework;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class ParameterUpdateTracking {
    @Pointcut("execution(public void edu.jhuapl.hlahelper.framework.InteractionValuesWrapper+.set*(*)) && !within(edu.jhuapl.hlahelper.framework.InteractionValuesWrapper)")
    public void changeTracked() {
    }

    @After("changeTracked()")
    public void changeTrack(JoinPoint jp) {
        String parameterName = jp.getStaticPart().getSignature().getName().substring(3);
        InteractionValuesWrapper ivw = (InteractionValuesWrapper) jp.getThis();
        ivw.setParameterUpdated(parameterName, true);
    }
}
