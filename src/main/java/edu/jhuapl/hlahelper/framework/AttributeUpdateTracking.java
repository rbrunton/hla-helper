package edu.jhuapl.hlahelper.framework;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class AttributeUpdateTracking {
    @Pointcut("execution(public void edu.jhuapl.hlahelper.framework.ObjectClassWrapper+.set*(*)) && !within(edu.jhuapl.hlahelper.framework.ObjectClassWrapper)")
    public void changeTracked() {
    }

    @After("changeTracked()")
    public void changeTrack(JoinPoint jp) {
        String attributeName = jp.getStaticPart().getSignature().getName().substring(3);
        ObjectClassWrapper ocw = (ObjectClassWrapper) jp.getThis();
        ocw.setAttributeUpdated(attributeName, true);
    }
}
