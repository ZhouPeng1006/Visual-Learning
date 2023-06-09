package asm;

abstract public class Assembly {
    abstract public String toSource(SymbolTable table);
    abstract public String dump();

    public void collectStatistics(Statistics stats) {
        // does nothing by default.
    }
}
