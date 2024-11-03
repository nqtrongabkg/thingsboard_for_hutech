/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.edge;

import com.google.protobuf.AbstractMessage;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.CustomerUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EdgeConfiguration;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class CustomerEdgeTest extends AbstractEdgeTest {

    @Test
    public void testCreateUpdateDeleteCustomer() throws Exception {
        // create customer
        edgeImitator.expectMessageAmount(1);
        Customer customer = new Customer();
        customer.setTitle("Edge Customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);
        Assert.assertFalse(edgeImitator.waitForMessages(5));

        // assign edge to customer
        edgeImitator.expectMessageAmount(2);
        doPost("/api/customer/" + savedCustomer.getUuidId()
                + "/edge/" + edge.getUuidId(), Edge.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        Optional<EdgeConfiguration> edgeConfigurationOpt = edgeImitator.findMessageByType(EdgeConfiguration.class);
        Assert.assertTrue(edgeConfigurationOpt.isPresent());
        EdgeConfiguration edgeConfiguration = edgeConfigurationOpt.get();
        Assert.assertEquals(savedCustomer.getUuidId().getMostSignificantBits(), edgeConfiguration.getCustomerIdMSB());
        Assert.assertEquals(savedCustomer.getUuidId().getLeastSignificantBits(), edgeConfiguration.getCustomerIdLSB());
        Optional<CustomerUpdateMsg> customerUpdateOpt = edgeImitator.findMessageByType(CustomerUpdateMsg.class);
        Assert.assertTrue(customerUpdateOpt.isPresent());
        CustomerUpdateMsg customerUpdateMsg = customerUpdateOpt.get();
        Customer customerMsg = JacksonUtil.fromString(customerUpdateMsg.getEntity(), Customer.class, true);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, customerUpdateMsg.getMsgType());
        Assert.assertEquals(savedCustomer, customerMsg);
        testAutoGeneratedCodeByProtobuf(customerUpdateMsg);

        // update customer
        edgeImitator.expectMessageAmount(1);
        savedCustomer.setTitle("Edge Customer Updated");
        savedCustomer = doPost("/api/customer", savedCustomer, Customer.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof CustomerUpdateMsg);
        customerUpdateMsg = (CustomerUpdateMsg) latestMessage;
        customerMsg = JacksonUtil.fromString(customerUpdateMsg.getEntity(), Customer.class, true);
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, customerUpdateMsg.getMsgType());
        Assert.assertEquals(savedCustomer, customerMsg);

        // delete customer
        edgeImitator.expectMessageAmount(2);
        doDelete("/api/customer/" + savedCustomer.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        edgeConfigurationOpt = edgeImitator.findMessageByType(EdgeConfiguration.class);
        Assert.assertTrue(edgeConfigurationOpt.isPresent());
        edgeConfiguration = edgeConfigurationOpt.get();
        Assert.assertEquals(
                new CustomerId(EntityId.NULL_UUID),
                new CustomerId(new UUID(edgeConfiguration.getCustomerIdMSB(), edgeConfiguration.getCustomerIdLSB())));
        customerUpdateOpt = edgeImitator.findMessageByType(CustomerUpdateMsg.class);
        Assert.assertTrue(customerUpdateOpt.isPresent());
        customerUpdateMsg = customerUpdateOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, customerUpdateMsg.getMsgType());
        Assert.assertEquals(customerUpdateMsg.getIdMSB(), savedCustomer.getUuidId().getMostSignificantBits());
        Assert.assertEquals(customerUpdateMsg.getIdLSB(), savedCustomer.getUuidId().getLeastSignificantBits());
    }

}
