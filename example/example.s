	.section .text
	.global _start
_start:
	li   a0, 10
	call fib
1:	j    1b

	.global fib
fib:
	li   t1, 0
	li   t2, 1
	li   t3, 0
loop:	bge  t3, a0, end
	add  t4, t1, t2
	mv   t1, t2
	mv   t2, t4
	addi t3, t3, 1
	j    loop
end:	mv   a0, t1
	ret
