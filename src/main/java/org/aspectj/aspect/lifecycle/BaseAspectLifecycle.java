package org.aspectj.aspect.lifecycle;

import org.aspectj.configuration.AspectJDescriptor;
import org.aspectj.configuration.model.Configuration;
import org.aspectj.configuration.model.ContextUtils;
import org.aspectj.configuration.model.Expression;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.util.MavenLoader;
import org.aspectj.util.Utils;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.UUID;

/**
 *
 */
public abstract class BaseAspectLifecycle {

    public static final String JOIN_POINT_VARIABLE = "joinPoint";

    private final org.aspectj.configuration.model.Aspect aspect;

    protected final Serializable processScript;

    protected VariableResolverFactory resolverFactory = new MapVariableResolverFactory(new HashMap<String, Object>());

    protected BaseAspectLifecycle() {
        String aspectName = getClass().getName();
        Configuration configuration = AspectJDescriptor.getConfiguration();
        checkConfiguration(configuration);
        aspect = configuration.getAspect(aspectName);
        registerGlobalResolverContext(configuration);
        MavenLoader.loadArtifact(aspect.getArtifacts(), resolverFactory);
        Utils.executeExpression(aspect.getInit(), resolverFactory);
        if(Expression.isNotEmptyExpression(aspect.getProcess())){
            processScript = Utils.compileMvelExpression(aspect.getProcess().getExpression());
        } else {
            processScript = null;
        }
        Utils.registerDisposeExpression(aspect.getDispose(), this.resolverFactory);
    }

    private void checkConfiguration(Configuration configuration) {
        if(configuration == null){
            Exception exception = null;
            try {
                Class<?> loadClass = BaseAspectLifecycle.class.getClassLoader().loadClass(UUID.randomUUID().toString());
            } catch (ClassNotFoundException e) {
                exception = e; //Capture class loader exception
            }

            throw new IllegalStateException("Shared aspect configuration not found. " +
                    "May be class loader isolate this aspect from loaded configuration. "+
                    "Shared configuration class hash: " + System.identityHashCode(AspectJDescriptor.class), exception);
        }
    }

    private void registerGlobalResolverContext(Configuration configuration) {
        VariableResolverFactory globalResolver = configuration.getGlobalResolver();
        if(globalResolver!=null) {
            resolverFactory.setNextFactory(globalResolver);
        }
    }

    protected Object processAround(ProceedingJoinPoint pjp) throws Throwable {
        if(processScript ==null){
            return pjp.proceed();
        } else {
            return process(pjp);
        }
    }

    protected Object process(JoinPoint pjp) throws Throwable {
        if(processScript ==null) return null;
        VariableResolverFactory variableResolverFactory = createProcessVariableResolver(pjp);
        return Utils.executeMvelExpression(processScript, variableResolverFactory);
    }

    protected void executeProcessWithException(JoinPoint joinPoint, Throwable exception) throws Throwable {
        if(processScript ==null) return;
        VariableResolverFactory variableResolverFactory = createProcessVariableResolver(joinPoint);
        variableResolverFactory.createVariable("exception", exception);
        Utils.executeMvelExpression(processScript, variableResolverFactory);
    }

    private VariableResolverFactory createProcessVariableResolver(JoinPoint pjp) {
        VariableResolverFactory variableResolverFactory = new MapVariableResolverFactory();
        Utils.fillResolveParams(aspect.getProcess().getResultParams(), variableResolverFactory);
        variableResolverFactory.createVariable(JOIN_POINT_VARIABLE, pjp);
        variableResolverFactory.createVariable(ContextUtils.VARIABLE_RESOLVER, variableResolverFactory);
        variableResolverFactory.setNextFactory(resolverFactory);
        return variableResolverFactory;
    }
}
