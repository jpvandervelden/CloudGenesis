package com.lifeway.cloudops.cloudformation

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.{DeleteStackRequest, DescribeStacksRequest}
import org.scalactic.{Bad, Good, Or}
import org.slf4j.LoggerFactory

/**
  * Delete stack executor. Given the StackConfig and S3File, do the delete of the given stack. If CF service raises
  * errors as part of the function call, then turn it into a Bad[StackError]. Any errors that happen Async outside of
  * the CF invoke will be caught by the SNS subscription on the CF stack itself. The job of this Lambda is not to
  * monitor the status, only to invoke the process and capture any errors that the CloudFormation service returns
  * directly as part of that invoke.
  *
  */
// $COVERAGE-OFF$
object DeleteStackExecutorDefaultFunctions extends StackExecutor {
  override val execute: (AmazonCloudFormation, StackConfig, S3File) => Or[Unit, AutomationError] =
    DeleteStackExecutor.execute
}
// $COVERAGE-ON$

object DeleteStackExecutor {
  val logger = LoggerFactory.getLogger("com.lifeway.cloudops.cloudformation.DeleteStackExecutor")

  def execute(cfClient: AmazonCloudFormation, config: StackConfig, s3File: S3File): Unit Or AutomationError =
    try {
      val stackReq   = new DescribeStacksRequest().withStackName(config.stackName)
      val stackFound = !cfClient.describeStacks(stackReq).getStacks.isEmpty
      if (stackFound) {
        val req = new DeleteStackRequest().withStackName(config.stackName)
        cfClient.deleteStack(req)
        Good(())
      } else {
        Bad(
          StackError(
            s"Failed to delete stack: ${s3File.key}. No stack by that stack name: ${config.stackName} exists!"))
      }
    } catch {
      case e: AmazonServiceException if e.getStatusCode >= 500 =>
        logger.error(s"AWS 500 Service Exception: Failed to delete stack: ${s3File.key}.", e)
        Bad(ServiceError(s"AWS 500 Service Exception: Failed to delete stack: ${s3File.key} due to: ${e.getMessage}"))
      case e: Throwable =>
        logger.error(s"Failed to delete stack: ${s3File.key}", e)
        Bad(StackError(s"Failed to delete stack: ${s3File.key} due to: ${e.getMessage}"))
    }
}
