#pragma once

#define UART_RBR ((volatile unsigned char*) 0x10000000) // receiver buffer register [ro]
#define UART_THR ((volatile unsigned char*) 0x10000000) // transmitter holding register [wo]
#define UART_IER ((volatile unsigned char*) 0x10000001) // interrupt enable register
#define UART_IIR ((volatile unsigned char*) 0x10000002) // interrupt identification register [ro]
#define UART_FCR ((volatile unsigned char*) 0x10000002) // fifo control register [wo]
#define UART_LCR ((volatile unsigned char*) 0x10000003) // line control register
#define UART_MCR ((volatile unsigned char*) 0x10000004) // modem control register
#define UART_LSR ((volatile unsigned char*) 0x10000005) // line status register
#define UART_MSR ((volatile unsigned char*) 0x10000006) // modem status register
#define UART_SCR ((volatile unsigned char*) 0x10000007) // scratch register

#define UART_IER_ERBFI 0b00000001                       // enable received data avaible interrupt
#define UART_IER_ETBEI 0b00000010                       // enable transmitter holding register empty interrupt
#define UART_IER_ELSI  0b00000100                       // enable receiver line status interrupt
#define UART_IER_EDSSI 0b00001000                       // enable modem status interrupt

#define UART_IIR_INT_PENDING     0b00000001             // 0 = interrupt pending, 1 = none
#define UART_IIR_INT_ID          0b00001110             // interrupt id
#define UART_IIR_FIFO_ENABLED    0b01000000             // fifo enabled
#define UART_IIR_FIFO_FUNCTIONAL 0b10000000             // fifo functional

#define UART_FCR_FIFO_ENABLE   0b00000001               // enable fifo mode
#define UART_FCR_RX_FIFO_RESET 0b00000010               // clear received fifo
#define UART_FCR_TX_FIFO_RESET 0b00000100               // clear transmit fifo
#define UART_FCR_TRIGGER_LEVEL 0b00111000               // rx trigger level

#define UART_LCR_WORD_LENGTH   0b00000011               // word length (5 bits + value)
#define UART_LCR_STOP_BITS     0b00000100               //
#define UART_LCR_PARITY        0b00111000               //
#define UART_LCR_BREAK_CONTROL 0b01000000               // force break condition
#define UART_LCR_DLAB          0b10000000               // divisor latch access bit

#define UART_MCR_DTR      0b00000001                    // data terminal ready
#define UART_MCR_RTS      0b00000010                    // request to send
#define UART_MCR_OUT1     0b00000100                    // user output 1
#define UART_MCR_OUT2     0b00001000                    // user output 2
#define UART_MCR_LOOPBACK 0b00010000                    // enable loopback test mode

#define UART_LSR_DR         0b00000001                  // data ready
#define UART_LSR_OE         0b00000010                  // overrun error
#define UART_LSR_PE         0b00000100                  // parity error
#define UART_LSR_FE         0b00001000                  // framing error
#define UART_LSR_BI         0b00010000                  // break interrupt
#define UART_LSR_THRE       0b00100000                  // transmitter holding register empty
#define UART_LSR_TEMT       0b01000000                  // transmitter empty
#define UART_LSR_FIFO_ERROR 0b10000000                  // error in received fifo

#define UART_MSR_DCTS 0b00000001                        // delta clear to send
#define UART_MSR_DDSR 0b00000010                        // delta data set ready
#define UART_MSR_TERI 0b00000100                        // trailing edge ring indicator
#define UART_MSR_DDCD 0b00001000                        // delta carrier detect
#define UART_MSR_CTS  0b00010000                        // clear to send
#define UART_MSR_DSR  0b00100000                        // data set ready
#define UART_MSR_RI   0b01000000                        // ring indicator
#define UART_MSR_DCD  0b10000000                        // carrier detect
