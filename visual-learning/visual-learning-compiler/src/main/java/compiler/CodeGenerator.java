package compiler;


import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMTargetMachineRef;
import org.bytedeco.llvm.LLVM.LLVMTargetRef;

import static org.bytedeco.llvm.global.LLVM.*;

public class CodeGenerator {
    public void asmGenerate(LLVMModuleRef module, String name) {
        LLVMInitializeAllTargetInfos();
        LLVMInitializeAllTargets();
        LLVMInitializeAllTargetMCs();
        LLVMInitializeAllAsmPrinters();
        LLVMInitializeAllAsmParsers();
        LLVMInitializeAllDisassemblers();
        LLVMTargetRef targetRef = LLVMGetTargetFromName("x86-64");
        // 创建机器目标机器
        PointerPointer error = new PointerPointer((Pointer) null);
        LLVMTargetMachineRef targetMachine = LLVMCreateTargetMachine(
                targetRef,
                "x86-64",
                "",
                "",
                LLVMCodeGenLevelDefault,
                LLVMRelocDefault,
                LLVMCodeModelDefault
        );
        if (LLVMTargetMachineEmitToFile(targetMachine, module, new BytePointer(name), LLVMAssemblyFile, error) != 0) {
            System.err.println("Could not emit asm file: " + error.getString(0));
        }
    }


}
