/*
 * Copyright (c) 2021 The StreamX Project
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.streamxhub.streamx.common.util

import com.streamxhub.streamx.common.fs.LfsOperator

import java.io.File
import org.apache.commons.io.{FileUtils => ApacheFileUtils}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import java.util.{Map => JavaMap}


/**
 * Hadoop client configuration tools mainly for flink use.
 *
 * @author Al-assad
 */
object HadoopConfigUtils {

  val HADOOP_CLIENT_CONF_FILES: Array[String] = Array("core-site.xml", "hdfs-site.xml", "yarn-site.xml")

  val HIVE_CLIENT_CONF_FILES: Array[String] = Array("core-site.xml", "hdfs-site.xml", "hive-site.xml")

  /**
   * Get Hadoop configuration directory path from system.
   */
  def getSystemHadoopConfDir: String =
    Try(
      Try(FileUtils.getPathFromEnv("HADOOP_CONF_DIR")) match {
        case Success(value) => value
        case Failure(_) => FileUtils.resolvePath(FileUtils.getPathFromEnv("HADOOP_HOME"), "/etc/hadoop")
      }
    ).getOrElse("")

  /**
   * Get Hive configuration directory path from system.
   */
  def getSystemHiveConfDir: String = Try(FileUtils.getPathFromEnv("HIVE_CONF_DIR")).getOrElse("")

  /**
   * Replace host information with ip of hadoop config file or hive config file.
   * Such as core-site.xml, hdfs-site.xml, hive-site.xml.
   */
  def replaceHostWithIP(configFile: File): Unit = {
    if (!configFile.exists || !configFile.isFile || !configFile.getName.endsWith(".xml"))
      return
    // get hosts from system
    val hostsMap = HostsUtils.getSystemHosts
    if (hostsMap.isEmpty)
      return
    rewriteHostIpMapper(configFile, hostsMap)
  }

  /**
   * Replace host information with ip of configuration files under hadoop/hive config dir.
   */
  def batchReplaceHostWithIP(configDir: File, filter: Array[String] = HADOOP_CLIENT_CONF_FILES): Unit = {
    if (!configDir.isDirectory) {
      replaceHostWithIP(configDir)
      return
    }
    val hostsMap = HostsUtils.getSystemHosts
    if (hostsMap.isEmpty)
      return
    configDir.listFiles
      .filter(_.isFile).filter(e => filter.contains(e.getName))
      .foreach(rewriteHostIpMapper(_, hostsMap))
  }

  private[this] def rewriteHostIpMapper(configFile: File, hostsMap: ListMap[String, String]): Unit = {
    // replace the host information in the configuration content
    val lines = ApacheFileUtils.readLines(configFile).asScala.map {
      case line if !line.trim.startsWith("<value>") => line
      case line =>
        var shot = hostsMap.find(e => line.contains(e._1))
        var li = line
        while (shot.nonEmpty) {
          li = li.replace(shot.get._1, shot.get._2)
          shot = hostsMap.find(e => li.contains(e._1))
        }
        li
    }
    // write content to original file
    ApacheFileUtils.writeLines(configFile, lines)
  }

  /**
   * Read system hadoop config to Map
   */
  def readSystemHadoopConf: JavaMap[String, String] =
    LfsOperator.listDir(getSystemHadoopConfDir)
      .filter(f => HADOOP_CLIENT_CONF_FILES.contains(f.getName))
      .map(f => f.getName -> ApacheFileUtils.readFileToString(f, "UTF-8"))
      .toMap.asJava

  /**
   * Read system hive config to Map
   */
  def readSystemHiveConf: JavaMap[String, String] =
    LfsOperator.listDir(getSystemHiveConfDir)
      .filter(f => HIVE_CLIENT_CONF_FILES.contains(f.getName))
      .map(f => f.getName -> ApacheFileUtils.readFileToString(f, "UTF-8"))
      .toMap.asJava


}
