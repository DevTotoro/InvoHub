package com.invohub.backend.infrastructure.adapter.inbound.web.support

import com.invohub.backend.domain.common.TSIDCodec
import org.springframework.format.AnnotationFormatterFactory
import org.springframework.format.Parser
import org.springframework.format.Printer

class TSIDFormatterFactory : AnnotationFormatterFactory<TSIDParam> {
    override fun getFieldTypes(): Set<Class<*>> = setOf(Long::class.java)

    override fun getPrinter(
        annotation: TSIDParam,
        fieldType: Class<*>,
    ): Printer<Long> = Printer { objectValue, _ -> TSIDCodec.encode(objectValue) }

    override fun getParser(
        annotation: TSIDParam,
        fieldType: Class<*>,
    ): Parser<Long> = Parser { text, _ -> TSIDCodec.decode(text) }
}
