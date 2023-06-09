import ast.AST;
import ast.*;
import checker.LocalResolver;
import checker.TypeChecker;
import checker.TypeResolver;
import compiler.*;
import entity.*;
import exception.*;
import ast.Op;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;
import org.junit.jupiter.api.Test;
import parser.Parser;
import type.*;
import type.TypeTable;
import utils.*;

import java.io.File;
import java.util.List;

import static org.bytedeco.llvm.global.LLVM.*;


public class LLVMIRGenerator {
    ErrorHandler errorHandler = new ErrorHandler("Test");

    String[] op = {"if.c"};

    public AST parseFile(String path, Options opts)
            throws FileException, SyntaxException {
        return Parser.parseFile(new File(path), errorHandler);
    }

    public AST semanticAnalyze(AST ast, TypeTable types,
                               Options opts) throws SemanticException {
        new LocalResolver(errorHandler).resolve(ast);
        new TypeResolver(types, errorHandler).resolve(ast);
        if (opts.mode() == CompilerMode.DumpReference) {
            ast.dump();
            return ast;
        }
        new TypeChecker(types, errorHandler).check(ast);
        return ast;
    }

    LLVMContextRef context;
    LLVMModuleRef module;
    LLVMBuilderRef builder;
    LLVMValueRef currFunc;

    @Test
    public void llvm_Test() throws FileException, SemanticException, SyntaxException {
        Options opts = Options.parse(op);
        List<SourceFile> sourceFiles = opts.sourceFiles();
        AST ast = parseFile(sourceFiles.get(0).path(), opts);
        TypeTable types = opts.typeTable();
        AST sem = semanticAnalyze(ast, types, opts);

        this.context = LLVMContextCreate();
        this.module = LLVMModuleCreateWithName(ast.source.sourceName());

        // Create a builder object
        this.builder = LLVMCreateBuilderInContext(context);

        // Transform topLevelScope variables
        for (DefinedVariable var : ast.definedVariables()) {
            definedVariablesGenerate(module, builder, var, ast.scope());
        }

        // Declare global constants
        for (Constant constant : ast.constants()) {
            LLVMTypeRef constType = typeToLLVMType(constant.type());
            LLVMValueRef llvmConst = LLVMAddGlobal(module, constType, constant.name());
            LLVMSetLinkage(llvmConst, LLVMInternalLinkage);
            LLVMValueRef llvmInitVal = constToLLVMValue(builder, constant.value());
            LLVMSetInitializer(llvmConst, llvmInitVal);
        }
        for (DefinedFunction func : ast.definedFunctions()) {
            LLVMTypeRef[] paramTypes = typesToLLVMTypes(func.parameters());
            LLVMTypeRef funcType = LLVMFunctionType(typeToLLVMType(func.returnType()), new PointerPointer<>(paramTypes),
                    paramTypes.length,
                    0
            );
            LLVMAddFunction(module, func.name(), funcType);
        }

        for (DefinedFunction func : ast.definedFunctions()) {
            LLVMValueRef llvmFunc = LLVMGetNamedFunction(module, func.name());
            this.currFunc = llvmFunc;
            int i = 0;
            for (DefinedVariable param : func.parameters()) {
                LLVMValueRef llvmParam = LLVMGetParam(llvmFunc, i);
                LLVMSetValueName(llvmParam, param.name());
                i++;
            }


            LLVMBasicBlockRef entry = LLVMAppendBasicBlock(llvmFunc, func.name() + ".entry");
            LLVMPositionBuilderAtEnd(builder, entry);

            // Generate function body
            stmtToLLVM(module, builder, func.body(), func.body().scope());
            LLVMPositionBuilderAtEnd(builder, LLVMGetEntryBasicBlock(currFunc));
        }

        LLVMDumpModule(module);
        asmGenerateTest(module);

        LLVMDisposeBuilder(builder);
        LLVMDisposeModule(module);
    }


    private LLVMValueRef stmtToLLVM(LLVMModuleRef module, LLVMBuilderRef builder, StmtNode node, Scope scope) throws SemanticException {
        if (node instanceof ExprStmtNode) {
            return exprToLLVM(module, builder, ((ExprStmtNode) node).expr(), scope);
        }
        else if (node instanceof ReturnNode) {
            ExprNode expr = ((ReturnNode) node).expr();
            LLVMValueRef ret = exprToLLVM(module, builder, expr, scope);
            return LLVMBuildRet(builder, ret);
        }
        else if (node instanceof BlockNode) {
            BlockNode body = ((BlockNode) node);
            List<StmtNode> stmts = body.stmts();
            List<DefinedVariable> variables = body.variables();

            for (DefinedVariable var : variables) {
                definedVariablesGenerate(module, builder, var, body.scope());
            }
            for (StmtNode stmt : stmts) {
                stmtToLLVM(module, builder, stmt, body.scope());
            }
        }
        else if (node instanceof IfNode) {
            LLVMBasicBlockRef condBlock = LLVMAppendBasicBlockInContext(context, currFunc, "if.cond");
            LLVMBasicBlockRef thenBlock = LLVMAppendBasicBlockInContext(context, currFunc, "if.then");
            LLVMBasicBlockRef elseBlock = LLVMAppendBasicBlockInContext(context, currFunc, "if.else");
            LLVMBasicBlockRef endBlock = LLVMAppendBasicBlockInContext(context, currFunc, "if.end");
            ExprNode cond = ((IfNode) node).cond();
            BlockNode thenBody = (BlockNode) ((IfNode) node).thenBody();
            BlockNode elseBody = (BlockNode) ((IfNode) node).elseBody();
            // Conditional branch
            LLVMBuildBr(builder, condBlock);
            LLVMPositionBuilderAtEnd(builder, condBlock);
            LLVMValueRef condRes = buildICmp(module, builder, cond, scope);
            if (elseBody != null) {
                LLVMBuildCondBr(builder, condRes, thenBlock, elseBlock);
            } else {
                LLVMBuildCondBr(builder, condRes, thenBlock, endBlock);
            }
            LLVMPositionBuilderAtEnd(builder, thenBlock);
            stmtToLLVM(module, builder, thenBody, thenBody.scope());
            LLVMBuildBr(builder, endBlock);

            if (elseBody != null) {
                LLVMPositionBuilderAtEnd(builder, elseBlock);
                stmtToLLVM(module, builder, elseBody, elseBody.scope());
                LLVMBuildBr(builder, endBlock);
            }
            LLVMPositionBuilderAtEnd(builder, endBlock);

        }
        else if (node instanceof ForNode) {
            LLVMBasicBlockRef loopCond = LLVMAppendBasicBlockInContext(context, currFunc, "loopCond.cond");
            LLVMBasicBlockRef loopBody = LLVMAppendBasicBlockInContext(context, currFunc, "loopCond.body");
            LLVMBasicBlockRef loopEnd = LLVMAppendBasicBlockInContext(context, currFunc, "loopCond.end");

            ExprNode init = ((ExprStmtNode) ((ForNode) node).init()).expr();
            ExprNode cond = ((ForNode) node).cond();
            // Set init
            setAlloca(module, builder, init, scope);
            LLVMBuildBr(builder, loopCond);
            LLVMPositionBuilderAtEnd(builder, loopCond);
            LLVMValueRef cmp = buildICmp(module, builder, cond, scope);
            LLVMBuildCondBr(builder, cmp, loopBody, loopEnd);
            LLVMPositionBuilderAtEnd(builder, loopBody);
            stmtToLLVM(module, builder, ((ForNode) node).body(), ((BlockNode) ((ForNode) node).body()).scope());
            // incr
            stmtToLLVM(module, builder, ((ForNode) node).incr(), ((BlockNode) ((ForNode) node).body()).scope());
            LLVMBuildBr(builder, loopCond);
            LLVMPositionBuilderAtEnd(builder, loopEnd);
        }
        else if (node instanceof WhileNode) {
            LLVMBasicBlockRef whileCondBlock = LLVMAppendBasicBlockInContext(context, currFunc, "while.cond");
            LLVMBasicBlockRef whileLoopBlock = LLVMAppendBasicBlockInContext(context, currFunc, "while.loop");
            LLVMBasicBlockRef endBlock = LLVMAppendBasicBlockInContext(context, currFunc, "while.end");
            ExprNode cond = ((WhileNode) node).cond();
            BlockNode body = (BlockNode) ((WhileNode) node).body();
            LLVMBuildBr(builder, whileCondBlock);

            LLVMPositionBuilderAtEnd(builder, whileCondBlock);
            LLVMValueRef cmp = buildICmp(module, builder, cond, scope);
            LLVMBuildCondBr(builder, cmp, whileLoopBlock, endBlock);

            LLVMPositionBuilderAtEnd(builder, whileLoopBlock);
            stmtToLLVM(module, builder, body, body.scope());
            LLVMBuildBr(builder, whileCondBlock);

            LLVMPositionBuilderAtEnd(builder, endBlock);
        }
        else if (node instanceof DoWhileNode){
            BlockNode body = (BlockNode)((DoWhileNode) node).body();
            ExprNode cond = ((DoWhileNode) node).cond();
            LLVMBasicBlockRef doWhileCond = LLVMAppendBasicBlockInContext(context, currFunc, "doWhile.cond");
            LLVMBasicBlockRef doWhileBody = LLVMAppendBasicBlockInContext(context, currFunc, "doWhile.body");
            LLVMBasicBlockRef doWhileEnd = LLVMAppendBasicBlockInContext(context, currFunc, "doWhile.end");

            LLVMBuildBr(builder,doWhileBody);
            LLVMPositionBuilderAtEnd(builder, doWhileBody);

            stmtToLLVM(module, builder, body, body.scope());

            LLVMBuildBr(builder, doWhileCond);
            LLVMPositionBuilderAtEnd(builder, doWhileCond);
            LLVMValueRef cmp = buildICmp(module, builder, cond, scope);
            LLVMBuildCondBr(builder,cmp, doWhileBody,doWhileEnd);

            LLVMPositionBuilderAtEnd(builder, doWhileEnd);


        }
        else if (node instanceof BreakNode) { }
        else if (node instanceof CaseNode) { }
        else if (node instanceof ContinueNode) {}
        else if (node instanceof LabelNode){
            String name = ((LabelNode) node).name();
            LLVMBasicBlockRef labelBlock = LLVMAppendBasicBlockInContext(context, currFunc, name);
            scope.putScopeBasicBlock(name, labelBlock);
            StmtNode body = ((LabelNode) node).stmt();
            LLVMPositionBuilderAtEnd(builder, labelBlock);
            stmtToLLVM(module,builder,body, scope);

        }
        else if (node instanceof GotoNode){
            String target = ((GotoNode) node).target();
            LLVMBasicBlockRef targetBlock = scope.getScopeBasicBlock(target);
            return LLVMBuildBr(builder, targetBlock);
        }
        return null;
    }

    private LLVMValueRef buildICmp(LLVMModuleRef module, LLVMBuilderRef builder, ExprNode expr, Scope scope) {
        if (expr instanceof LogicalAndNode) {
            LLVMValueRef cmpR = buildICmp(module, builder, ((BinaryOpNode) expr).right(), scope);
            LLVMValueRef cmpL = buildICmp(module, builder, ((BinaryOpNode) expr).left(), scope);
            return LLVMBuildAnd(builder, cmpL, cmpR, "and");

        }
        else if (expr instanceof LogicalOrNode) {
            LLVMValueRef cmpR = buildICmp(module, builder, ((BinaryOpNode) expr).right(), scope);
            LLVMValueRef cmpL = buildICmp(module, builder, ((BinaryOpNode) expr).left(), scope);
            return LLVMBuildOr(builder, cmpL, cmpR, "or");
        }
        else {
            ExprNode right = ((BinaryOpNode) expr).right();
            long cmpVal = ((IntegerLiteralNode) right).value();

            VariableNode left = (VariableNode) ((BinaryOpNode) expr).left();
            String op = ((BinaryOpNode) expr).operator();
            int iCmpOp = getICmpOp(op);
            LLVMValueRef lhs = LLVMBuildLoad(builder, scope.getAlloca(left.name()), left.name());
            return LLVMBuildICmp(builder, iCmpOp, lhs, LLVMConstInt(LLVMInt32TypeInContext(context), cmpVal, 0), "cmp");

        }
    }

    private static LLVMValueRef exprToLLVM(LLVMModuleRef module, LLVMBuilderRef builder, ExprNode expr, Scope scope) {
        if (expr instanceof BinaryOpNode) {
            String operator = ((BinaryOpNode) expr).operator();
            BinaryOpNode node = ((BinaryOpNode) expr);
            LLVMValueRef right = exprToLLVM(module, builder, node.right(), scope);
            LLVMValueRef left = exprToLLVM(module, builder, node.left(), scope);
            if (((BinaryOpNode) expr).left().isConstant() && ((BinaryOpNode) expr).right().isConstant()) {
                IntegerLiteralNode l = (IntegerLiteralNode) ((BinaryOpNode) expr).left();
                IntegerLiteralNode r = (IntegerLiteralNode) ((BinaryOpNode) expr).right();
                long value = constantFold(l, operator, r);
                return constToLLVMValue(value, builder);
            } else {
                return compileBinaryOp(module, builder, right, left, node.operator(), scope);
            }
        } else if (expr instanceof VariableNode) {
            Entity e = ((VariableNode) expr).entity();
            if (e.isConstant()) {
                ExprNode value = e.value();
                if (value instanceof IntegerLiteralNode) {
                    long value1 = ((IntegerLiteralNode) value).value();
                    LLVMValueRef yPtr = LLVMBuildAlloca(builder, LLVMInt32Type(), e.name());
                    return LLVMBuildStore(builder,
                            LLVMConstInt(LLVMInt32Type(), value1, 0),
                            yPtr);
                }
            } else if (e.isDefined()) {
                LLVMValueRef var = scope.getAlloca(e.name());
                return LLVMBuildLoad(builder, var, e.name());
            }
        } else if (expr instanceof IntegerLiteralNode) {
            // Literal value
            long value = ((IntegerLiteralNode) expr).value();
            LLVMTypeRef valueType = typeToLLVMType(expr.type());
            return LLVMConstInt(valueType, value, 0);
        } else if (expr instanceof FuncallNode) {
            DefinedFunction func = (DefinedFunction) ((VariableNode) ((FuncallNode) expr).expr()).entity();
            List<ExprNode> args = ((FuncallNode) expr).args();

            LLVMValueRef function = LLVMGetNamedFunction(module, func.name());
            LLVMTypeRef functionTypeRef = LLVMGetElementType(LLVMTypeOf(function));
            LLVMValueRef[] params = argsToLLVMValue(module, builder, args, scope);
            PointerPointer<LLVMValueRef> llvmValueRefPointerPointer = new PointerPointer<>(params);
            return LLVMBuildCall2(builder, functionTypeRef, function, llvmValueRefPointerPointer, params.length, func.name());
        } else if (expr instanceof AssignNode) {
            AssignNode node = (AssignNode) expr;
            LLVMValueRef rhs = exprToLLVM(module, builder, node.rhs(), scope);
            LLVMValueRef orig = scope.getAlloca(((VariableNode) node.lhs()).name());

            return LLVMBuildStore(builder, rhs, orig);
        } else if (expr instanceof OpAssignNode) {
            OpAssignNode node = (OpAssignNode) expr;
            LLVMValueRef rhs = exprToLLVM(module, builder, node.rhs(), scope);
            LLVMValueRef lhs = exprToLLVM(module, builder, node.lhs(), scope);
            // cont(lhs += rhs) -> lhs = lhs + rhs; cont(lhs)
            LLVMValueRef res = compileBinaryOp(module, builder, rhs, lhs, node.operator(), scope);
            LLVMValueRef orig = scope.getAlloca(((VariableNode) node.lhs()).name());
            return LLVMBuildStore(builder, res, orig);

        } else if (expr instanceof SuffixOpNode) {
            ExprNode node = ((SuffixOpNode) expr).expr();
            LLVMValueRef i = scope.getAlloca(((VariableNode) node).name());

            LLVMValueRef load = LLVMBuildLoad(builder, i, ((VariableNode) node).name());
            LLVMValueRef add = LLVMBuildAdd(builder, load, LLVMConstInt(LLVMInt32Type(), 1, 0), "add");
            LLVMBuildStore(builder, add, i);
        } else if (expr instanceof UnaryOpNode) {
            if (((UnaryOpNode) expr).operator().equals("+")) {
                constToLLVMValue(builder, expr);
            } else {
                return transformUnary(module, builder, expr, scope);
            }
        }
        return null;
    }

    private static LLVMValueRef transformUnary(LLVMModuleRef module, LLVMBuilderRef builder, ExprNode expr, Scope scope) {
        UnaryOpNode node = (UnaryOpNode) expr;
        LLVMValueRef val = exprToLLVM(module, builder, node.expr(), scope);
        switch (node.operator()) {
            case "-":
                return LLVMBuildNeg(builder, val, "neg");
            case "~":
                return LLVMBuildNot(builder, val, "xor");
            case "!":
                return LLVMBuildNot(builder, val, "not");
        }
        return null;
    }

    public static LLVMValueRef setAlloca(LLVMModuleRef module, LLVMBuilderRef builder, ExprNode expr, Scope scope) {
        if (expr instanceof AssignNode) {
            VariableNode lhs = (VariableNode) ((AssignNode) expr).lhs();
            LLVMValueRef var = scope.getAlloca(lhs.name());
            ExprNode rhs = ((AssignNode) expr).rhs();
            if (rhs instanceof IntegerLiteralNode) {
                LLVMValueRef var1 = exprToLLVM(module, builder, rhs, scope);
                LLVMBuildStore(builder, var1, var);
            } else if (rhs instanceof UnaryOpNode) {

            }
        } else if (expr instanceof VariableNode) {

        }
        return null;
    }

    public static LLVMValueRef buildAlloca(LLVMModuleRef module, LLVMBuilderRef builder, DefinedVariable var, Scope scope) throws SemanticException {
        if (scope.getAlloca(var.name()) != null) {
            return scope.getAlloca(var.name());
        } else {
            LLVMTypeRef varType = typeToLLVMType(var.typeNode().type());
            LLVMValueRef alloca = LLVMBuildAlloca(builder, varType, var.name());
            scope.putAlloca(var.name(), alloca);
            return alloca;
        }
    }


    private static long constantFold(IntegerLiteralNode l, String operator, IntegerLiteralNode r) {
        long L = l.value();
        long R = r.value();
        switch (operator) {
            case "+":
                return L + R;
            case "-":
                return L - R;
            case "*":
                return L * R;
            case "/":
                return L / R;
            case "%":
                return L % R;
        }
        throw new Error("error occurred while fold constant: " + L + operator + R);
    }


    private static LLVMValueRef compileBinaryOp(LLVMModuleRef module, LLVMBuilderRef builder,
                                                LLVMValueRef right, LLVMValueRef left, String operator, Scope scope) {

        Op op = Op.internBinary(operator, false);
        LLVMValueRef ret = null;
        switch (op) {
            case ADD:
                ret = LLVMBuildAdd(builder, left, right, "add");
                break;
            case SUB:
                ret = LLVMBuildSub(builder, left, right, "sub");
                break;
            case MUL:
                ret = LLVMBuildMul(builder,left,ret,"mul");
//                break;
//            case S_DIV:
//                break;
//            case S_MOD:
//                if (op == Op.S_MOD) {
//                }
//                break;
//            case U_DIV:
//            case U_MOD:
//                if (op == Op.U_MOD) {
//                }
//                break;
//            case BIT_AND:
//                break;
//            case BIT_OR:
//                break;
//            case BIT_XOR:
//                break;
//            case BIT_LSHIFT:
//                break;
//            case BIT_RSHIFT:
//                break;
//            case ARITH_RSHIFT:
//                break;
//            default:
//                // Comparison operators
////                as.cmp(right, ax(left.type));
//                switch (op) {
//                    case EQ:
//                        break;
//                    case NEQ:
//                        break;
//                    case S_GT:
//                        break;
//                    case S_GTEQ:
//                        break;
//                    case S_LT:
//                        break;
//                    case S_LTEQ:
//                        break;
//                    case U_GT:
//                        break;
//                    case U_GTEQ:
//                        break;
//                    case U_LT:
//                        break;
//                    case U_LTEQ:
//                        break;
//                    default:
//                        throw new Error("unknown binary operator: " + op);
//                }
        }
        return ret;

    }

    public void asmGenerateTest(LLVMModuleRef module) {
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
        if (LLVMTargetMachineEmitToFile(targetMachine, module, new BytePointer("output.s"), LLVMAssemblyFile, error) != 0) {
            System.err.println("Could not emit asm file: " + error.getString(0));
        }
    }


    private static LLVMValueRef definedVariablesGenerate(LLVMModuleRef module, LLVMBuilderRef builder, DefinedVariable var, Scope scope) throws SemanticException {
        LLVMValueRef ret;
        LLVMTypeRef varType = typeToLLVMType(var.typeNode().type());
        if (scope.isToplevel()) {
            LLVMValueRef llvmVar = LLVMAddGlobal(module, varType, var.name());
            LLVMSetLinkage(llvmVar, LLVMExternalLinkage);
            if (var.hasInitializer()) {
                LLVMValueRef llvmInitVal = constToLLVMValue( builder,var.initializer());
                LLVMSetInitializer(llvmVar, llvmInitVal);
            }
            ret = llvmVar;
        } else {
            LLVMValueRef localVar = buildAlloca(module, builder, var, scope);
            if (var.hasInitializer()) {
                IntegerLiteralNode intExpr = (IntegerLiteralNode) var.initializer();
                long value = intExpr.value();
                LLVMBuildStore(builder, LLVMConstInt(LLVMInt32Type(), value, 0), localVar);
            }
            ret = localVar;
        }
        return ret;
    }


    private static LLVMTypeRef typeToLLVMType(Type type) {
        if (type instanceof IntegerType) {
            return LLVMInt32Type();
        } else if (type instanceof PointerType) {
            Type baseType = type.baseType();
            LLVMTypeRef baseLLVMType = typeToLLVMType(baseType);
            return LLVMPointerType(baseLLVMType, 0);
        } else if (type instanceof ArrayType) {
            Type elementType = type.baseType();
            long numElements = ((ArrayType) type).length();
            LLVMTypeRef elementLLVMType = typeToLLVMType(elementType);
            return LLVMArrayType(elementLLVMType, (int) numElements);
        } else if (type instanceof VoidType) {
            return LLVMVoidType();
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
        }
    }


    private static LLVMValueRef constToLLVMValue(long value, LLVMBuilderRef builder) {
        return LLVMConstInt(LLVMInt32Type(), value, 0);

    }

    private static LLVMValueRef constToLLVMValue(LLVMBuilderRef builder, ExprNode expr) {
        if (expr instanceof IntegerLiteralNode) {
            IntegerLiteralNode intExpr = (IntegerLiteralNode) expr;
            long value = intExpr.value();
            return LLVMConstInt(LLVMInt32Type(), value, 0);
        } else if (expr instanceof StringLiteralNode) {
            StringLiteralNode strExpr = (StringLiteralNode) expr;
            String value = strExpr.value();
            LLVMValueRef strPtr = LLVMConstString(value, value.length(), 1);
            LLVMValueRef[] indices = {LLVMConstInt(LLVMInt32Type(), 0, 0), LLVMConstInt(LLVMInt32Type(), 0, 0)};
            return LLVMBuildInBoundsGEP2(builder, LLVMPointerType(LLVMInt8Type(), 0), strPtr, new PointerPointer(indices), 2, "");
        } else {
            throw new IllegalArgumentException("Unsupported constant type: " + expr.getClass().getName());
        }
    }

    private static LLVMTypeRef[] typesToLLVMTypes(List<Parameter> variables) {
        LLVMTypeRef[] llvmTypes = new LLVMTypeRef[variables.size()];
        for (int i = 0; i < variables.size(); i++) {
            Variable variable = variables.get(i);
            LLVMTypeRef llvmType = typeToLLVMType(variable.type());

            // If the variable is an array, wrap the LLVM type in a pointer type
            if (variable.type() instanceof ArrayType) {
                llvmType = LLVMPointerType(llvmType, 0);
            }
            llvmTypes[i] = llvmType;
        }
        return llvmTypes;
    }

    private static LLVMValueRef[] argsToLLVMValue(LLVMModuleRef module, LLVMBuilderRef builder, List<ExprNode> args, Scope scope) {
        LLVMValueRef[] llvmTypes = new LLVMValueRef[args.size()];
        for (int i = 0; i < args.size(); i++) {
            ExprNode exprNode = args.get(i);
            LLVMValueRef argVal = null;
            if (args.get(i) instanceof VariableNode) {
                argVal = scope.getAlloca(((VariableNode) args.get(i)).name());

            } else if (args.get(i) instanceof IntegerLiteralNode) {
                argVal = exprToLLVM(module, builder, exprNode, scope);

            }
            llvmTypes[i] = argVal;
        }
        return llvmTypes;
    }


    public static int getICmpOp(String operator) {
        switch (operator) {
            case "<":
                return LLVMIntSLT;
            case "<=":
                return LLVMIntSLE;
            case ">=":
                return LLVMIntSGE;
            case ">":
                return LLVMIntSGT;
            case "==":
                return LLVMIntEQ;
            case "!=":
                return LLVMIntNE;
        }
        return 0;
    }

}
