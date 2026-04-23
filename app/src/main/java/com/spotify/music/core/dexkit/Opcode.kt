package com.spotify.music.core.dexkit

/**
 * Dalvik/DEX opcode representations for DexKit matching.
 * Opcode values are from the Dalvik Executable (DEX) format specification.
 * Used in fingerprint queries to match specific bytecode patterns.
 */
enum class Opcode(val value: Int) {
    // Invoke opcodes (0x6e - 0x72)
    INVOKE_VIRTUAL(0x6e),
    INVOKE_SUPER(0x6f),
    INVOKE_DIRECT(0x70),
    INVOKE_STATIC(0x71),
    INVOKE_INTERFACE(0x72),
    
    // Move opcodes (0x01 - 0x09)
    MOVE(0x01),
    MOVE_WIDE(0x04),
    MOVE_OBJECT(0x07),
    MOVE_RESULT(0x0a),
    MOVE_RESULT_WIDE(0x0b),
    MOVE_RESULT_OBJECT(0x0c),
    MOVE_EXCEPTION(0x0d),
    
    // Return opcodes (0x0e - 0x11)
    RETURN_VOID(0x0e),
    RETURN(0x0f),
    RETURN_WIDE(0x10),
    RETURN_OBJECT(0x11),
    
    // Const opcodes (0x12 - 0x1a)
    CONST_4(0x12),
    CONST_16(0x13),
    CONST(0x14),
    CONST_HIGH16(0x15),
    CONST_WIDE_16(0x16),
    CONST_WIDE_32(0x17),
    CONST_WIDE(0x18),
    CONST_WIDE_HIGH16(0x19),
    CONST_STRING(0x1a),
    CONST_STRING_JUMBO(0x1b),
    CONST_CLASS(0x1c),
    
    // Monitor opcodes (0x1d - 0x1e)
    MONITOR_ENTER(0x1d),
    MONITOR_EXIT(0x1e),
    
    // Check/Array opcodes (0x1f - 0x23)
    CHECK_CAST(0x1f),
    INSTANCE_OF(0x20),
    ARRAY_LENGTH(0x21),
    NEW_INSTANCE(0x22),
    NEW_ARRAY(0x23),
    FILLED_NEW_ARRAY(0x24),
    FILLED_NEW_ARRAY_RANGE(0x25),
    FILL_ARRAY_DATA(0x26),
    THROW(0x27),
    GOTO(0x28),
    GOTO_16(0x29),
    GOTO_32(0x2a),
    
    // Packed/Sparse switch (0x2b - 0x2c)
    PACKED_SWITCH(0x2b),
    SPARSE_SWITCH(0x2c),
    
    // Compare opcodes (0x2d - 0x31)
    CMP_LONG(0x31),
    CMPKIND_FLOAT_LT(0x2d),
    CMPKIND_FLOAT_GT(0x2e),
    CMPKIND_DOUBLE_LT(0x2f),
    CMPKIND_DOUBLE_GT(0x30),
    
    // If opcodes (0x32 - 0x3d)
    IF_EQ(0x32),
    IF_NE(0x33),
    IF_LT(0x34),
    IF_GE(0x35),
    IF_GT(0x36),
    IF_LE(0x37),
    IF_EQZ(0x38),
    IF_NEZ(0x39),
    IF_LTZ(0x3a),
    IF_GEZ(0x3b),
    IF_GTZ(0x3c),
    IF_LEZ(0x3d),
    
    // Array access (0x44 - 0x51)
    AGET(0x44),
    AGET_WIDE(0x45),
    AGET_OBJECT(0x46),
    AGET_BOOLEAN(0x47),
    AGET_BYTE(0x48),
    AGET_CHAR(0x49),
    AGET_SHORT(0x4a),
    APUT(0x4b),
    APUT_WIDE(0x4c),
    APUT_OBJECT(0x4d),
    APUT_BOOLEAN(0x4e),
    APUT_BYTE(0x4f),
    APUT_CHAR(0x50),
    APUT_SHORT(0x51),
    
    // Field access (0x52 - 0x65)
    IGET(0x52),
    IGET_WIDE(0x53),
    IGET_OBJECT(0x54),
    IGET_BOOLEAN(0x55),
    IGET_BYTE(0x56),
    IGET_CHAR(0x57),
    IGET_SHORT(0x58),
    IPUT(0x59),
    IPUT_WIDE(0x5a),
    IPUT_OBJECT(0x5b),
    IPUT_BOOLEAN(0x5c),
    IPUT_BYTE(0x5d),
    IPUT_CHAR(0x5e),
    IPUT_SHORT(0x5f),
    SGET(0x60),
    SGET_WIDE(0x61),
    SGET_OBJECT(0x62),
    SGET_BOOLEAN(0x63),
    SGET_BYTE(0x64),
    SGET_CHAR(0x65),
    SGET_SHORT(0x66),
    SPUT(0x67),
    SPUT_WIDE(0x68),
    SPUT_OBJECT(0x69),
    SPUT_BOOLEAN(0x6a),
    SPUT_BYTE(0x6b),
    SPUT_CHAR(0x6c),
    SPUT_SHORT(0x6d),
    
    // Binary opcodes (0x7b - 0xc1)
    ADD_INT(0x90),
    SUB_INT(0x91),
    MUL_INT(0x92),
    DIV_INT(0x93),
    REM_INT(0x94),
    AND_INT(0x95),
    OR_INT(0x96),
    XOR_INT(0x97),
    SHL_INT(0x98),
    SHR_INT(0x99),
    USHR_INT(0x9a),
    AND_INT_LIT8(0xdd),
}

