package sabotage.bin
import curl.*
import sabotage.lib.*
import java.nio.file.Path
import scala.util.Try

import scalanative.unsafe.*
import scalanative.libc.*

import language.experimental.saferExceptions

class CurlNetwork private (inst: Ptr[CURL]) extends Network:
  override def downloadFile(url: String, path: Path): Unit throws NetworkError =
    Zone:
      val cPath = toCString(path.toAbsolutePath().toString())
      val cUrl = toCString(url)
      curl_easy_setopt(inst, CURLoption.CURLOPT_URL, cUrl)

      val fp = stdio.fopen(cPath, c"wb")

      val write_data_callback = CFuncPtr4.fromScalaFunction {
        (
            ptr: Ptr[Byte],
            size: CSize,
            nmemb: CSize,
            userdata: Ptr[stdio.FILE]
        ) =>
          stdio.fwrite(ptr, size, nmemb, userdata)
      }

      curl_easy_setopt(
        inst,
        CURLoption.CURLOPT_WRITEFUNCTION,
        write_data_callback
      )
      curl_easy_setopt(inst, CURLoption.CURLOPT_WRITEDATA, fp)

      val res = curl_easy_perform(inst)
      assert(res == CURLcode.CURLE_OK, "Expected request to succeed")
      stdio.fclose(fp)

end CurlNetwork

object CurlNetwork:
  def use[A](f: Network ?=> A) =
    val curl = curl_easy_init()
    assert(curl != null, "Failed to initialise curl instance")
    try
      val inst = CurlNetwork(curl)
      f(using inst)
    finally curl_easy_cleanup(curl)
