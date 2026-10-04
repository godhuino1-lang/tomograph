package io.github.godhuino1.tomograph.api;

/**
 * 一个模块想看到的一个方法，用 <b>方法名 + 描述符</b> 标识。
 *
 * <p><b>描述符不是可选项，这正是这个类型存在的全部理由。</b> 促使它诞生的那个框架目标，
 * 把同一个方法声明了两次：
 *
 * <pre>
 * doChat(ChatRequest) -&gt; ChatResponse                             （阻塞式）
 * doChat(ChatRequest) -&gt; java.util.concurrent.Flow$Publisher      （响应式，@since 1.20.0）
 * </pre>
 *
 * <p>同名、同参数、<b>只有返回值不同</b>。Java 不允许在同一个类里这样写，所以框架把它们放在了两个接口上；
 * 但一个只看方法名的字节码匹配器会把它们当成同一个东西——**要么两个都插桩，要么把为 A 写的代码插到 B 上**。
 * 结果是宿主应用在加载类时抛 {@code VerifyError}，**这是本项目能造成的最坏的失败**。见 ADR 0006。
 *
 * <p>描述符用的是 class 文件格式自己的语法，例如
 * {@code (Ldev/langchain4j/model/chat/request/ChatRequest;)Ldev/langchain4j/model/chat/response/ChatResponse;}
 * ——见 JVMS 4.3。
 *
 * @param name       方法名，例如 {@code doChat}
 * @param descriptor 完整描述符，<b>包含返回值</b>
 */
public record MethodCutPoint(String name, String descriptor) {

    public MethodCutPoint {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("方法名不能为空");
        }
        if (descriptor == null || descriptor.isBlank()) {
            throw new IllegalArgumentException("描述符不能为空（原因见 MethodCutPoint 的类注释）");
        }
        if (descriptor.indexOf('(') < 0) {
            throw new IllegalArgumentException("这必须是方法描述符（带括号），实际收到：" + descriptor);
        }
    }

    public static MethodCutPoint of(String name, String descriptor) {
        return new MethodCutPoint(name, descriptor);
    }

    /** 日志用的短形式：{@code doChat(Ldev/...;)Ldev/...;}。 */
    public String display() {
        return name + descriptor;
    }
}
