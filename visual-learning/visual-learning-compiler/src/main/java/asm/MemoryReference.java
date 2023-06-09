package asm;

abstract public class MemoryReference
        extends Operand implements Comparable<MemoryReference> {
    public boolean isMemoryReference() {
        return true;
    }

    abstract protected int cmp(DirectMemoryReference mem);
    abstract protected int cmp(IndirectMemoryReference mem);
}
