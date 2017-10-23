package spring.session

import org.grails.testing.GrailsUnitTest
import spock.lang.Specification
import utils.SpringSessionUtils

/**
 * See the API for {@link grails.test.mixin.support.GrailsUnitTestMixin} for usage instructions
 */
class SpringSessionUtilsSpec extends Specification implements GrailsUnitTest {

    void "Canary test"() {
        expect:
        true
    }

    void "test getSessionConfig"() {
        setup:
        SpringSessionUtils.config = null
        SpringSessionUtils.application = getGrailsApplication()

        when:
        ConfigObject configObject = SpringSessionUtils.getSessionConfig()

        then:
        configObject
        configObject.maxInactiveInterval == 1800
    }

    void "test getSessionConfig with overrided values"() {
        setup:
        SpringSessionUtils.config = null
        SpringSessionUtils.application = getGrailsApplication()
        SpringSessionUtils.application.config.springsession = configObject

        when:
        ConfigObject resultConfig = SpringSessionUtils.getSessionConfig()

        then:
        resultConfig.redis.connectionFactory.hostName == hostname
        SpringSessionUtils.application.config.springsession.redis.connectionFactory.hostName == hostname
        resultConfig.redis.connectionFactory.port == SpringSessionUtils.application.config.springsession.redis.connectionFactory.port

        where:
        description                  | configObject                                                          | hostname
        "Null Config object"         | null                                                                  | "localhost"
        "Changed host Config object" | [redis: [connectionFactory: [hostName: "127.0.0.1"]]] as ConfigObject | "127.0.0.1"
    }
}