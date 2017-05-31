/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.lang.*;
import org.ofbiz.base.util.*;
import org.ofbiz.entity.*;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.accounting.payment.*;
import org.ofbiz.order.order.*;
import org.ofbiz.party.contact.*;
import org.ofbiz.product.catalog.*;
import org.ofbiz.product.store.*;
import org.ofbiz.webapp.website.WebSiteWorker;

cart = session.getAttribute("shoppingCart");
context.cart = cart;

orderItems = cart.makeOrderItems();
context.orderItems = orderItems;

orderAdjustments = cart.makeAllAdjustments();

orderItemShipGroupInfo = cart.makeAllShipGroupInfos();
if (orderItemShipGroupInfo) {
    orderItemShipGroupInfo.each { valueObj ->
        if ("OrderAdjustment".equals(valueObj.getEntityName())) {
            // shipping / tax adjustment(s)
            orderAdjustments.add(valueObj);
        }
    }
}
context.orderAdjustments = orderAdjustments;

workEfforts = cart.makeWorkEfforts();   // if required make workefforts for rental fixed assets too.
context.workEfforts = workEfforts;

orderHeaderAdjustments = OrderReadHelper.getOrderHeaderAdjustments(orderAdjustments, null);
context.orderHeaderAdjustments = orderHeaderAdjustments;
context.orderItemShipGroups = cart.getShipGroups();
context.headerAdjustmentsToShow = OrderReadHelper.filterOrderAdjustments(orderHeaderAdjustments, true, false, false, false, false);

orderSubTotal = OrderReadHelper.getOrderItemsSubTotal(orderItems, orderAdjustments, workEfforts);
context.orderSubTotal = orderSubTotal;
context.placingCustomerPerson = userLogin?.getRelatedOne("Person", false);
context.paymentMethods = cart.getPaymentMethods();

paymentMethodTypeIds = cart.getPaymentMethodTypeIds();
paymentMethodType = null;
paymentMethodTypeId = null;
/* SCIPIO: This contradicts OrderStatus.groovy. paymentMethodType should only be set to a 
if (paymentMethodTypeIds) {
    paymentMethodTypeId = paymentMethodTypeIds[0];
    paymentMethodType = from("PaymentMethodType").where("paymentMethodTypeId", paymentMethodTypeId).queryOne();
    context.paymentMethodType = paymentMethodType;
}*/
paymentMethodTypeIdsNoPaymentMethodIds = cart.getPaymentMethodTypeIdsNoPaymentMethodIds();
if (paymentMethodTypeIdsNoPaymentMethodIds) {
    paymentMethodTypeId = paymentMethodTypeIdsNoPaymentMethodIds[0];
    paymentMethodType = from("PaymentMethodType").where("paymentMethodTypeId", paymentMethodTypeId).queryOne();
    context.paymentMethodType = paymentMethodType;
}

webSiteId = WebSiteWorker.getWebSiteId(request);

productStore = ProductStoreWorker.getProductStore(request);
context.productStore = productStore;

isDemoStore = !"N".equals(productStore.isDemoStore);
context.isDemoStore = isDemoStore;

payToPartyId = productStore.payToPartyId;
paymentAddress = PaymentWorker.getPaymentAddress(delegator, payToPartyId);
if (paymentAddress) context.paymentAddress = paymentAddress;


// TODO: FIXME!
/*
billingAccount = cart.getBillingAccountId() ? delegator.findOne("BillingAccount", [billingAccountId : cart.getBillingAccountId()], false) : null;
if (billingAccount)
    context.billingAccount = billingAccount;
*/

context.customerPoNumber = cart.getPoNumber();
context.carrierPartyId = cart.getCarrierPartyId();
context.shipmentMethodTypeId = cart.getShipmentMethodTypeId();
context.shippingInstructions = cart.getShippingInstructions();
context.maySplit = cart.getMaySplit();
context.giftMessage = cart.getGiftMessage();
context.isGift = cart.getIsGift();
context.currencyUomId = cart.getCurrency();

shipmentMethodType = from("ShipmentMethodType").where("shipmentMethodTypeId", cart.getShipmentMethodTypeId()).queryOne();
if (shipmentMethodType) context.shipMethDescription = shipmentMethodType.description;

orh = new OrderReadHelper(orderAdjustments, orderItems);
context.localOrderReadHelper = orh;
context.orderShippingTotal = cart.getTotalShipping();
context.orderTaxTotal = cart.getTotalSalesTax();
context.orderVATTaxTotal = cart.getTotalVATTax();
context.orderGrandTotal = cart.getGrandTotal();

// nuke the event messages
request.removeAttribute("_EVENT_MESSAGE_");

// SCIPIO: Get placing party
placingPartyId = cart.getPlacingCustomerPartyId();
context.placingPartyId = placingPartyId;
placingParty = null;
if (placingPartyId) {
    // emulates OrderReadHelper
    placingParty = EntityQuery.use(delegator).from("Person").where("partyId", placingPartyId).queryOne();
    if (!placingParty) {
        placingParty = EntityQuery.use(delegator).from("PartyGroup").where("partyId", placingPartyId).queryOne();
    }
}
context.placingParty = placingParty;

// SCIPIO: Get order date. If it's not yet set, use nowTimestamp
context.orderDate = cart.getOrderDate() ?: nowTimestamp;

// SCIPIO: Get emails (all combined)
context.orderEmailList = cart.getOrderEmailList();

// SCIPIO: exact payment amounts for all pay types
context.paymentMethodAmountMap = cart.getPaymentAmountsByIdOrType();

// SCIPIO: Subscriptions
// SCIPIO: Check if the order has underlying subscriptions
context.subscriptionItems = orh.getItemSubscriptions();
context.subscriptions = orh.hasSubscriptions();
// SCIPIO: TODO: We may add more paymentMethodTypeIds in the future
context.validPaymentMethodTypeForSubscriptions = (UtilValidate.isNotEmpty(cart) && cart.getPaymentMethodTypeIds().contains("EXT_PAYPAL"));
context.orderContainsSubscriptionItemsOnly = orh.orderContainsSubscriptionItemsOnly();