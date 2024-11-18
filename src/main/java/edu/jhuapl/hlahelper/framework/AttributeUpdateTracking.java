/*
 * © 2024  The Johns Hopkins University Applied Physics Laboratory LLC.
 */

package edu.jhuapl.hlahelper.framework;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * Aspect for tracking attribute updates in ObjectClassWrapper subclasses.
 */
@Aspect
public class AttributeUpdateTracking {
    /**
     * Pointcut for all set methods in ObjectClassWrapper subclasses.
     */
    @Pointcut("execution(public void edu.jhuapl.hlahelper.framework.ObjectClassWrapper+.set*(*)) && !within(edu.jhuapl.hlahelper.framework.ObjectClassWrapper)")
    public void changeTracked() {
    }

    /**
     * After advice for the changeTracked pointcut. This method is called after any set method in an ObjectClassWrapper
     * and tracks that an attribute has been updated.
     * @param jp
     */
    @After("changeTracked()")
    public void changeTrack(JoinPoint jp) {
        String attributeName = jp.getStaticPart().getSignature().getName().substring(3);
        ObjectClassWrapper ocw = (ObjectClassWrapper) jp.getThis();
        ocw.setAttributeUpdated(attributeName, true);
    }
}
