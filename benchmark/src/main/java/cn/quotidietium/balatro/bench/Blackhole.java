package cn.quotidietium.balatro.bench;

/**
 * 防止 JIT 死码消除的结果汇。全部场景的计算结果都汇入本类的 volatile 字段，
 * 测量结束后打印，确保被测代码不会被优化掉。
 */
public final class Blackhole {
    public static volatile long SINK;

    public static void consume(long v) {
        SINK += v;
    }

    public static void consume(Object o) {
        SINK += System.identityHashCode(o);
    }

    private Blackhole() {
    }
}
