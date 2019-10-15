import com.google.inject.AbstractModule
import org.apache.ibatis.logging.LogFactory

class Module extends AbstractModule {
  override def configure() = {
    LogFactory.useSlf4jLogging()
  }
}
