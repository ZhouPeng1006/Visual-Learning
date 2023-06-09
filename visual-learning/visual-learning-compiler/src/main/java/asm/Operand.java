package asm;

abstract public class Operand {
    abstract public String toSource(SymbolTable table);
    abstract public String dump();


    abstract public void collectStatistics(Statistics stats);

}
