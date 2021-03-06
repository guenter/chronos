package org.apache.mesos.chronos.scheduler.jobs

import java.util.concurrent.ConcurrentHashMap
import java.util.logging.{Level, Logger}

import org.apache.mesos.chronos.scheduler.config.CassandraConfiguration
import com.datastax.driver.core._
import com.datastax.driver.core.exceptions.{DriverException, NoHostAvailableException, QueryExecutionException, QueryValidationException}
import com.google.inject.Inject
import org.apache.mesos.Protos.TaskStatus

import scala.collection.JavaConverters._

class JobStats @Inject()(clusterBuilder: Option[Cluster.Builder], config: CassandraConfiguration) {

  val log = Logger.getLogger(getClass.getName)
  val statements = new ConcurrentHashMap[String, PreparedStatement]().asScala
  var _session: Option[Session] = None

  def jobStarted(job: BaseJob, taskStatus: TaskStatus, attempt: Int) {
    try {
      getSession match {
        case Some(session: Session) =>
          job match {
            case job: ScheduleBasedJob =>
              val query =
                s"INSERT INTO ${config.cassandraTable()} (id, ts, job_name, job_owner, job_schedule, task_state, slave_id, attempt) VALUES (?, ?, ?, ?, ?, ?, ?, ?) USING TTL ${config.cassandraTtl()}"
              val prepared = statements.getOrElseUpdate(query, {
                session.prepare(
                  new SimpleStatement(query).setConsistencyLevel(ConsistencyLevel.valueOf(config.cassandraConsistency())).asInstanceOf[RegularStatement]
                )
              })
              session.executeAsync(prepared.bind(
                taskStatus.getTaskId.getValue,
                new java.util.Date(),
                job.name,
                job.owner,
                job.schedule,
                taskStatus.getState.toString,
                taskStatus.getSlaveId.getValue,
                attempt: java.lang.Integer
              ))
            case job: DependencyBasedJob =>
              val query =
                s"INSERT INTO ${config.cassandraTable()} (id, ts, job_name, job_owner, job_parents, task_state, slave_id, attempt) VALUES (?, ?, ?, ?, ?, ?, ?, ?) USING TTL ${config.cassandraTtl()}"
              val prepared = statements.getOrElseUpdate(query, {
                session.prepare(
                  new SimpleStatement(query).setConsistencyLevel(ConsistencyLevel.valueOf(config.cassandraConsistency())).asInstanceOf[RegularStatement]
                )
              })
              val parentSet: java.util.Set[String] = job.parents.asJava
              session.executeAsync(prepared.bind(
                taskStatus.getTaskId.getValue,
                new java.util.Date(),
                job.name,
                job.owner,
                parentSet,
                taskStatus.getState.toString,
                taskStatus.getSlaveId.getValue,
                attempt: java.lang.Integer
              ))
          }
        case None =>
      }
    } catch {
      case e: NoHostAvailableException =>
        resetSession()
        log.log(Level.WARNING, "No hosts were available, will retry next time.", e)
      case e: QueryExecutionException =>
        log.log(Level.WARNING, "Query execution failed:", e)
      case e: QueryValidationException =>
        log.log(Level.WARNING, "Query validation failed:", e)
    }
  }

  def getSession: Option[Session] = {
    _session match {
      case Some(s) => Some(s)
      case None =>
        clusterBuilder match {
          case Some(c) =>
            try {
              val session = c.build.connect(config.cassandraKeyspace())
              session.execute(new SimpleStatement(
                s"CREATE TABLE IF NOT EXISTS ${config.cassandraTable()}" +
                  """
                    |(
                    |   id             VARCHAR,
                    |   ts             TIMESTAMP,
                    |   job_name       VARCHAR,
                    |   job_owner      VARCHAR,
                    |   job_schedule   VARCHAR,
                    |   job_parents    SET<VARCHAR>,
                    |   task_state     VARCHAR,
                    |   slave_id       VARCHAR,
                    |   message        VARCHAR,
                    |   attempt        INT,
                    |   is_failure     BOOLEAN,
                    | PRIMARY KEY (id, ts))
                    | WITH bloom_filter_fp_chance=0.100000 AND
                    | compaction = {'class':'LeveledCompactionStrategy'}
                  """.stripMargin
              ))
              _session = Some(session)
              _session
            } catch {
              case e: DriverException =>
                log.log(Level.WARNING, "Caught exception when creating Cassandra JobStats session", e)
                None
            }
          case None => None
        }
    }
  }

  def resetSession() {
    statements.clear()
    _session match {
      case Some(session) =>
        session.close()
      case _ =>
    }
    _session = None
  }

  def jobFinished(job: BaseJob, taskStatus: TaskStatus, attempt: Int) {
    try {
      getSession match {
        case Some(session: Session) =>
          job match {
            case job: ScheduleBasedJob =>
              val query =
                s"INSERT INTO ${config.cassandraTable()} (id, ts, job_name, job_owner, job_schedule, task_state, slave_id, attempt) VALUES (?, ?, ?, ?, ?, ?, ?, ?) USING TTL ${config.cassandraTtl()}"
              val prepared = statements.getOrElseUpdate(query, {
                session.prepare(
                  new SimpleStatement(query).setConsistencyLevel(ConsistencyLevel.valueOf(config.cassandraConsistency())).asInstanceOf[RegularStatement]
                )
              })
              session.executeAsync(prepared.bind(
                taskStatus.getTaskId.getValue,
                new java.util.Date(),
                job.name,
                job.owner,
                job.schedule,
                taskStatus.getState.toString,
                taskStatus.getSlaveId.getValue,
                attempt: java.lang.Integer
              ))
            case job: DependencyBasedJob =>
              val query =
                s"INSERT INTO ${config.cassandraTable()} (id, ts, job_name, job_owner, job_parents, task_state, slave_id, attempt) VALUES (?, ?, ?, ?, ?, ?, ?, ?) USING TTL ${config.cassandraTtl()}"
              val prepared = statements.getOrElseUpdate(query, {
                session.prepare(
                  new SimpleStatement(query).setConsistencyLevel(ConsistencyLevel.valueOf(config.cassandraConsistency())).asInstanceOf[RegularStatement]
                )
              })
              val parentSet: java.util.Set[String] = job.parents.asJava
              session.execute(prepared.bind(
                taskStatus.getTaskId.getValue,
                new java.util.Date(),
                job.name,
                job.owner,
                parentSet,
                taskStatus.getState.toString,
                taskStatus.getSlaveId.getValue,
                attempt: java.lang.Integer
              ))
          }
        case None =>
      }
    } catch {
      case e: NoHostAvailableException =>
        resetSession()
        log.log(Level.WARNING, "No hosts were available, will retry next time.", e)
      case e: QueryExecutionException =>
        log.log(Level.WARNING, "Query execution failed:", e)
      case e: QueryValidationException =>
        log.log(Level.WARNING, "Query validation failed:", e)
    }
  }

  def jobFailed(job: BaseJob, taskStatus: TaskStatus, attempt: Int) {
    try {
      getSession match {
        case Some(session: Session) =>
          job match {
            case job: ScheduleBasedJob =>
              val query =
                s"INSERT INTO ${config.cassandraTable()} (id, ts, job_name, job_owner, job_schedule, task_state, slave_id, attempt, message, is_failure) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true) USING TTL ${config.cassandraTtl()}"
              val prepared = statements.getOrElseUpdate(query, {
                session.prepare(
                  new SimpleStatement(query).setConsistencyLevel(ConsistencyLevel.valueOf(config.cassandraConsistency())).asInstanceOf[RegularStatement]
                )
              })
              session.executeAsync(prepared.bind(
                taskStatus.getTaskId.getValue,
                new java.util.Date(),
                job.name,
                job.owner,
                job.schedule,
                taskStatus.getState.toString,
                taskStatus.getSlaveId.getValue,
                attempt: java.lang.Integer,
                taskStatus.getMessage
              ))
            case job: DependencyBasedJob =>
              val query =
                s"INSERT INTO ${config.cassandraTable} (id, ts, job_name, job_owner, job_parents, task_state, slave_id, attempt, message, is_failure) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true) USING TTL ${config.cassandraTtl()}"
              val prepared = statements.getOrElseUpdate(query, {
                session.prepare(
                  new SimpleStatement(query).setConsistencyLevel(ConsistencyLevel.valueOf(config.cassandraConsistency())).asInstanceOf[RegularStatement]
                )
              })
              val parentSet: java.util.Set[String] = job.parents.asJava
              session.executeAsync(prepared.bind(
                taskStatus.getTaskId.getValue,
                new java.util.Date(),
                job.name,
                job.owner,
                parentSet,
                taskStatus.getState.toString,
                taskStatus.getSlaveId.getValue,
                attempt: java.lang.Integer,
                taskStatus.getMessage
              ))
          }
        case None =>
      }
    } catch {
      case e: NoHostAvailableException =>
        resetSession()
        log.log(Level.WARNING, "No hosts were available, will retry next time.", e)
      case e: QueryExecutionException =>
        log.log(Level.WARNING, "Query execution failed:", e)
      case e: QueryValidationException =>
        log.log(Level.WARNING, "Query validation failed:", e)
    }
  }
}
