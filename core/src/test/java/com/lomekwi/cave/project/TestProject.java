package com.lomekwi.cave.project;

/**
 * 测试辅助类：把 Project 的 protected 构造器暴露为 public，
 * 供其它包（如 timeline 的单元测试）创建真实 Project。
 */
public class TestProject extends Project {
    public TestProject() {
        super();
    }
}
