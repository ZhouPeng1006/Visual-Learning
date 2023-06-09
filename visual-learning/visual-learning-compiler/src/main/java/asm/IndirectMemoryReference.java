package asm;

public class IndirectMemoryReference extends MemoryReference {
    Literal offset;
    Register base;
    boolean fixed;



    public void collectStatistics(Statistics stats) {
        base.collectStatistics(stats);
    }

    public String toString() {
        return toSource(SymbolTable.dummy());
    }

    public String toSource(SymbolTable table) {
        if (! fixed) {
            throw new Error("must not happen: writing unfixed variable");
        }
        return (offset.isZero() ? "" : offset.toSource(table))
                + "(" + base.toSource(table) + ")";
    }

    public int compareTo(MemoryReference mem) {
        return -(mem.cmp(this));
    }

    protected int cmp(DirectMemoryReference mem) {
        return -1;
    }

    protected int cmp(IndirectMemoryReference mem) {
        return offset.compareTo(mem.offset);
    }

    public String dump() {
        return "(IndirectMemoryReference "
                + (fixed ? "" : "*")
                + offset.dump() + " " + base.dump() + ")";
    }
}
