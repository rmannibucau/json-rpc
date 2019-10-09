package com.github.rmannibucau.jsonrpc.impl.cdi;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.BeforeShutdown;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessBean;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.config.PropertyOrderStrategy;
import javax.json.spi.JsonProvider;

import com.github.rmannibucau.jsonrpc.annotations.JsonRpcException;
import com.github.rmannibucau.jsonrpc.annotations.JsonRpcMethod;
import com.github.rmannibucau.jsonrpc.annotations.JsonRpcParam;
import com.github.rmannibucau.jsonrpc.configuration.Configuration;
import com.github.rmannibucau.jsonrpc.configuration.MicroprofileInitializer;
import com.github.rmannibucau.jsonrpc.impl.HandlerRegistry;
import com.github.rmannibucau.jsonrpc.protocol.JsonRpcHandler;
import com.github.rmannibucau.jsonrpc.qualifier.JsonRpc;

public class JsonRpcExtension implements Extension {
    private static final JsonRpcException[] EMPTY_EXCEPTION_ARRAY = new JsonRpcException[0];

    // enables to override default instances just by producing it
    private Bean<Jsonb> jsonbBean;
    private Bean<Configuration> configurationBean;

    private final HandlerRegistry registry = new HandlerRegistry();
    private final Map<Bean<?>, AnnotatedType<?>> rpcBeans = new HashMap<>();
    private final Collection<CreationalContext<?>> creationalContexts = new ArrayList<>();
    private final Collection<HandlerRegistry.Unregisterable> registrations = new ArrayList<>();

    void registerDefaultBeans(@Observes final BeforeBeanDiscovery beforeBeanDiscovery,
                              final BeanManager beanManager) {
        Stream.concat(
                Stream.of(HandlerRegistry.class, JsonRpcHandler.class, Configuration.class),
                tryLoad("com.github.rmannibucau.eventrpc.servlet.JsonRpcServlet"))
            .forEach(clazz -> beforeBeanDiscovery.addAnnotatedType(beanManager.createAnnotatedType(clazz)));
    }

    void captureRpcMethods(@Observes final ProcessBean<?> pb) {
        if (hasRpcMethod(pb.getAnnotated())) {
            rpcBeans.put(pb.getBean(), AnnotatedType.class.cast(pb.getAnnotated()));
        }
    }

    void captureJsonb(@Observes final ProcessBean<Jsonb> jsonbProcessBean) {
        final Bean<Jsonb> bean = jsonbProcessBean.getBean();
        if (bean.getQualifiers().contains(JsonRpc.Literal.INSTANCE)) {
            jsonbBean = bean;
        }
    }

    void captureConfiguration(@Observes final ProcessBean<Configuration> configurationProcessBean) {
        configurationBean = configurationProcessBean.getBean();
    }

    void registerBeans(@Observes final AfterBeanDiscovery afterBeanDiscovery, final BeanManager beanManager) {
        if (jsonbBean == null) {
            afterBeanDiscovery.<Jsonb>addBean()
                    .id("event_rpc::jsonb")
                    .scope(ApplicationScoped.class)
                    .qualifiers(JsonRpc.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .beanClass(Jsonb.class)
                    .types(Jsonb.class, Object.class)
                    .createWith(c -> JsonbBuilder.create(new JsonbConfig()
                            .withPropertyOrderStrategy(PropertyOrderStrategy.LEXICOGRAPHICAL)))
                    .destroyWith((jsonb, c) -> {
                        try {
                            jsonb.close();
                        } catch (final Exception e) {
                            // no-op
                        }
                    });
        }
        if (configurationBean == null) {
            afterBeanDiscovery.<Configuration>addBean()
                    .id("event_rpc::configuration")
                    .scope(ApplicationScoped.class)
                    .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .beanClass(Configuration.class)
                    .types(Configuration.class, Object.class)
                    .createWith(c -> MicroprofileInitializer.load(new Configuration()));
        }
        afterBeanDiscovery.<HandlerRegistry>addBean()
                .id("event_rpc::registry")
                .scope(ApplicationScoped.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .beanClass(HandlerRegistry.class)
                .types(HandlerRegistry.class, Object.class)
                .createWith(c -> registry);
    }

    void registerBeans(@Observes final AfterDeploymentValidation afterDeploymentValidation,
                       final BeanManager beanManager) {
        if (configurationBean == null) { // unlikely but just a guard
            configurationBean = (Bean<Configuration>) beanManager.resolve(beanManager.getBeans(Configuration.class));
        }
        if (!doLookup(beanManager, configurationBean).isActive()) {
            return;
        }

        ofNullable(doLookup(beanManager, configurationBean).getSpecificationMethod())
                .filter(it -> !it.isEmpty())
                .ifPresent(registry::registerSpecificationMethod);

        if (jsonbBean == null) { // unlikely but just a guard
            jsonbBean = (Bean<Jsonb>) beanManager.resolve(beanManager.getBeans(Jsonb.class, JsonRpc.Literal.INSTANCE));
        }
        registry.setJsonb(doLookup(beanManager, jsonbBean));
        registry.setJsonProvider(JsonProvider.provider());

        rpcBeans.forEach((bean, annotatedType) -> registerBean(beanManager, bean, annotatedType));
        rpcBeans.clear();
    }

    void cleanup(@Observes final BeforeShutdown beforeShutdown) {
        registrations.forEach(it -> safeRun(it::close));
        creationalContexts.forEach(it -> safeRun(it::release));
    }

    private void registerBean(final BeanManager beanManager, final Bean<?> bean, final AnnotatedType<?> annotatedType) {
        final Object instance = doLookup(beanManager, bean);
        registrations.addAll(annotatedType.getMethods().stream()
            .filter(method -> method.isAnnotationPresent(JsonRpcMethod.class))
            .map(method -> registry.registerMethodReflect(
                    instance, method.getJavaMember(),
                    method.getAnnotation(JsonRpcMethod.class),
                    method.getParameters().stream()
                            .map(p -> p.getAnnotation(JsonRpcParam.class))
                            .toArray(JsonRpcParam[]::new),
                    ofNullable(method.getAnnotations(JsonRpcException.class))
                            .map(a -> a.toArray(EMPTY_EXCEPTION_ARRAY))
                            .orElse(EMPTY_EXCEPTION_ARRAY)))
            .collect(toList()));
    }

    private <A> A doLookup(final BeanManager beanManager, final Bean<A> bean) {
        final CreationalContext<A> creationalContext = beanManager.createCreationalContext(null);
        final A instance = (A) beanManager.getReference(bean, bean.getBeanClass(), creationalContext);
        if (!beanManager.isNormalScope(bean.getScope())) {
            creationalContexts.add(creationalContext);
        }
        return instance;
    }

    private boolean hasRpcMethod(final Annotated annotated) {
        return AnnotatedType.class.isInstance(annotated) &&
                ((AnnotatedType<?>) annotated).getMethods().stream()
                    .anyMatch(it -> it.isAnnotationPresent(JsonRpcMethod.class));
    }

    private Stream<? extends Class<?>> tryLoad(final String... names) {
        final ClassLoader loader = ofNullable(Thread.currentThread().getContextClassLoader()).orElseGet(ClassLoader::getSystemClassLoader);
        return Stream.of(names).map(it -> {
            try {
                return loader.loadClass(it);
            } catch (final NoClassDefFoundError | ClassNotFoundException e) {
                return null;
            }
        }).filter(Objects::nonNull);
    }

    private void safeRun(final Runnable runnable) {
        try {
            runnable.run();
        } catch (final RuntimeException re) {
            Logger.getLogger(JsonRpcExtension.class.getName()).log(Level.SEVERE, re.getMessage(), re);
        }
    }
}
