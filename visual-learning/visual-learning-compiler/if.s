	.text
	.file	"if.c"
	.globl	main
	.p2align	4, 0x90
	.type	main,@function
main:
	.cfi_startproc
	subl	$8, %esp
	.cfi_def_cfa_offset 12
	movl	$5, (%esp)
	xorl	%eax, %eax
	testb	%al, %al
	jne	.LBB0_3
	movl	$5, %eax
	cmpl	$4, %eax
	jg	.LBB0_3
	incl	(%esp)
.LBB0_3:
	movl	%esp, %eax
	pushl	$3
	.cfi_adjust_cfa_offset 4
	pushl	%eax
	.cfi_adjust_cfa_offset 4
	calll	add
	addl	$8, %esp
	.cfi_adjust_cfa_offset -8
	movl	%eax, 4(%esp)
	movl	(%esp), %eax
	addl	$8, %esp
	.cfi_def_cfa_offset 4
	retl
.Lfunc_end0:
	.size	main, .Lfunc_end0-main
	.cfi_endproc

	.globl	add
	.p2align	4, 0x90
	.type	add,@function
add:
	.cfi_startproc
	xorl	%eax, %eax
	retl
.Lfunc_end1:
	.size	add, .Lfunc_end1-add
	.cfi_endproc

	.section	".note.GNU-stack","",@progbits
