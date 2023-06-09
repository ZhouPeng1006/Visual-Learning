package asm;

public interface Literal extends Comparable<Literal> {
    public String toSource();
    public String toSource(SymbolTable table);
    public String dump();
    public void collectStatistics(Statistics stats);
    public boolean isZero();
    public Literal plus(long diff);
    public int cmp(IntegerLiteral i);

}
