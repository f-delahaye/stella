package org.stella.ai.utils

import java.io.{ByteArrayOutputStream, ByteArrayInputStream}
import java.util.zip.{GZIPOutputStream, GZIPInputStream}

import scala.util.Try

object GZip {

  def compress(input: Array[Byte]): Array[Byte] = {
    val bos = new ByteArrayOutputStream(input.length)
    val gzip = new GZIPOutputStream(bos)
    gzip.write(input)
    gzip.close()
    val compressed = bos.toByteArray
    bos.close()
    compressed
  }

  def decompress(compressed: Array[Byte]): String = {
    val inputStream = new GZIPInputStream(new ByteArrayInputStream(compressed))
    scala.io.Source.fromInputStream(inputStream).mkString
  }
}