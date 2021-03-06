/*
 * Copyright 2009-2010 LinkedIn, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.linkedin.norbert.cluster.memory

import actors.Actor
import Actor._
import collection.immutable.IntMap
import com.linkedin.norbert.cluster.{InvalidNodeException, Node, ClusterNotificationManagerComponent, ClusterManagerComponent}
import com.linkedin.norbert.cluster.common.ClusterManagerHelper

trait InMemoryClusterManagerComponent extends ClusterManagerComponent with ClusterManagerHelper {
  this: ClusterNotificationManagerComponent =>

  class InMemoryClusterManager extends Actor {
    private var currentNodes: Map[Int, Node] = IntMap.empty
    private var available = Set[Int]()

    def act() = {
      actor {
        // Give the ClusterNotificationManager a chance to start
        Thread.sleep(100)
        clusterNotificationManager ! ClusterNotificationMessages.Connected(currentNodes)
      }

      while (true) {
        import ClusterManagerMessages._

        receive {
          case AddNode(node) => if (currentNodes.contains(node.id)) {
            reply(ClusterManagerResponse(Some(new InvalidNodeException("A node with id %d already exists".format(node.id)))))
          } else {
            val n = if (available.contains(node.id)) {
              Node(node.id, node.url, node.partitions, true)
            } else {
              Node(node.id, node.url, node.partitions, false)
            }

            currentNodes += (n.id -> n)
            clusterNotificationManager ! ClusterNotificationMessages.NodesChanged(currentNodes)
            reply(ClusterManagerResponse(None))
          }

          case RemoveNode(nodeId) =>
            currentNodes -= nodeId
            clusterNotificationManager ! ClusterNotificationMessages.NodesChanged(currentNodes)
            reply(ClusterManagerResponse(None))

          case MarkNodeAvailable(nodeId) =>
            currentNodes.get(nodeId).foreach { node =>
              currentNodes = currentNodes.update(nodeId, Node(node.id, node.url, node.partitions, true))
            }
            available += nodeId
            clusterNotificationManager ! ClusterNotificationMessages.NodesChanged(currentNodes)
            reply(ClusterManagerResponse(None))

          case MarkNodeUnavailable(nodeId) =>
            currentNodes.get(nodeId).foreach { node =>
              currentNodes = currentNodes.update(nodeId, Node(node.id, node.url, node.partitions, false))
            }
            available -= nodeId
            clusterNotificationManager ! ClusterNotificationMessages.NodesChanged(currentNodes)
            reply(ClusterManagerResponse(None))

          case Shutdown => exit
        }
      }
    }
  }
}
