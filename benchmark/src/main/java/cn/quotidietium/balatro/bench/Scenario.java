package cn.quotidietium.balatro.bench;

/**
 * 一个基准场景：每次 {@link #runBatch()} 执行固定数量的操作并返回该数量。
 * 每批工作量必须恒定且确定（固定种子），否则优化前后的数字不可比。
 */
public interface Scenario {
    String name();

    String description();

    long runBatch();
}
