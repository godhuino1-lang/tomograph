package io.github.godhuino1.tomograph.api;

import java.util.Set;

/**
 * 一个插桩模块：知道怎么改写某个框架的字节码。
 *
 * <p>通过 {@link java.util.ServiceLoader} 被发现，所以**第三方可以在完全不碰 Tomograph 源码的情况下**
 * 支持一个新框架。这种可扩展性就是这个 SPI 存在的全部理由：它是这个项目在第三年还能继续长大、
 * 而不需要作者亲手写每一个适配器的原因。
 *
 * <p>实现会在**类加载路径上**被调用。三条规矩：
 * <ul>
 *   <li>返回 {@code null} 表示"这个类别动"。</li>
 *   <li>**绝不抛异常**：引擎本来就会捕获 {@link Throwable} 并丢弃整次改写，所以抛出去只会丢数据。</li>
 *   <li>**要快**。这段代码运行在类正在被定义的时候，而且持有一把 JVM 锁。</li>
 * </ul>
 */
public interface TomographModule {

    /** 稳定标识，用于日志和 {@code tomograph.module} 属性，例如 {@code langchain4j}。 */
    String id();

    /**
     * 本模块想看到的类（**内部名**，斜杠分隔，和 class 文件格式一致），
     * 例如 {@code dev/langchain4j/model/chat/ChatModel}。
     *
     * <p>引擎会用这个集合建一个索引，所以"没人在意的类"的代价是**一次哈希查找**，而不是调用 N 个模块。
     */
    Set<String> targetClassNames();

    /**
     * 本模块想看到的方法——**不管它们由哪个类声明**。
     *
     * <p>为什么需要这个：框架的扩展点常常是一个**接口**。给接口插桩没有任何效果，代码在实现类里，
     * 而实现类的集合是**开放的**——厂商会发布新的、应用会自己写、测试会塞替身。
     * 维护一份"已知类名清单"会随每次上游发布而漂移，还会静默漏掉用户自己的实现。
     *
     * <p>所以模块可以直接声明一个**签名**。描述符里的参数类型通常带着框架的包名，
     * 这让描述符本身成为一个很强的判别式：光看 {@code doChat} 什么都有可能，
     * 但 {@code doChat(Ldev/langchain4j/model/chat/request/ChatRequest;)...} 就是 LangChain4j 的。
     *
     * <p>**代价，说清楚**：按签名匹配意味着引擎必须去看那些本来一次哈希查找就能跳过的类。
     * 它做得很便宜——方法名必然存在于该类的常量池里，所以一次字节级搜索能在任何解析发生之前
     * 刷掉几乎所有的类——但这仍然是**在别人的类加载路径上加活**。
     * **只在类名清单确实不适用时**才声明签名切点。
     *
     * <p>动机（LangChain4j 的案例），以及"两个看起来合理的选择各自如何在没有报错的情况下
     * 产出一棵错误的调用树"，见 ADR 0006。
     */
    default Set<MethodCutPoint> targetMethods() {
        return Set.of();
    }

    /**
     * 改写给定的类；返回 {@code null} 表示不动它。
     *
     * @param loader            定义这个类的类加载器，bootstrap 类可能是 {@code null}
     * @param internalClassName 斜杠分隔的类名
     * @param classfileBuffer   当前字节（可能已经被前一个模块改写过了）
     * @return 新的类字节，或 {@code null} 表示"没有改动"
     */
    byte[] instrument(ClassLoader loader, String internalClassName, byte[] classfileBuffer) throws Exception;

    /**
     * 在开始任何转换之前调用一次，把模块该发往的 span sink 交给它。
     *
     * <p><b>由引擎调用，不是由调用方调用</b>——引擎自己就是那个 {@link Runtime}。
     * 这曾经是调用方的责任，而那个契约是隐式的：集成测试直接构造了引擎、没有调它，
     * 于是模块静默地什么都不产出（探针的 sink 还留在"什么都不做"的默认值上）。
     */
    default void onInstall(Runtime runtime) {
        // 默认什么都不做
    }

    /** 安装时交给模块的东西。 */
    interface Runtime {

        SpanSink sink();

        AgentOptions options();
    }
}
