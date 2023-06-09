package asm;

abstract public class Register extends Operand {
    public void collectStatistics(Statistics stats) {
        stats.registerUsed(this);
    }

    abstract public String toSource(SymbolTable syms);
    abstract public String dump();
}
