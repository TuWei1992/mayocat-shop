/**
 * Copyright (c) 2012, Mayocat <hello@mayocat.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mayocat.shop.marketplace.web

import com.google.common.collect.Maps
import groovy.transform.CompileStatic
import org.mayocat.context.WebContext
import org.mayocat.rest.Resource
import org.mayocat.shop.checkout.CheckoutRegister
import org.mayocat.shop.checkout.CheckoutResponse
import org.mayocat.shop.checkout.CheckoutSettings
import org.mayocat.shop.customer.model.Address
import org.mayocat.shop.customer.model.Customer
import org.mayocat.shop.customer.store.AddressStore
import org.mayocat.shop.customer.store.CustomerStore
import org.mayocat.shop.front.views.WebView
import org.mayocat.shop.marketplace.web.object.CheckoutResponseWebObject
import org.mayocat.shop.marketplace.web.object.CheckoutWebObject
import org.slf4j.Logger
import org.xwiki.component.annotation.Component

import javax.inject.Inject
import javax.inject.Provider
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

/**
 * @version $Id$
 */
@Component("/marketplace/checkout")
@Path("/marketplace/checkout")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@CompileStatic
class MarketplaceCheckoutWebView implements Resource
{
    @Inject
    CheckoutSettings checkoutSettings

    @Inject
    WebContext webContext;

    @Inject
    Provider<CustomerStore> customerStore

    @Inject
    Provider<AddressStore> addressStore

    @Inject
    CheckoutRegister checkoutRegister

    @Inject
    Logger logger

    @POST
    public Object checkout(CheckoutWebObject checkoutWebObject)
    {
        if (!webContext.user && !checkoutSettings.isGuestCheckoutEnabled()) {
            return Response.status(Response.Status.FORBIDDEN).
                    entity("Configuration does not allow guest checkout").build()
        } else if (!webContext.user) {
            // For now, we only support non-guest checkout
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).
                    entity("Not supported").build()
        }

        Map<String, Object> otherData = Maps.newHashMap()

        Customer customer = customerStore.get().findByUserId(webContext.user.id)
        Address deliveryAddress = addressStore.get().findByCustomerIdAndType(customer.id, "delivery")
        Address billingAddress = addressStore.get().findByCustomerIdAndType(customer.id, "billing")

        try {
            CheckoutResponse response = checkoutRegister.
                    checkout(customer, deliveryAddress, billingAddress, otherData);
            CheckoutResponseWebObject checkoutResponseWebObject = new CheckoutResponseWebObject();
            if (response.getRedirectURL().isPresent()) {
                checkoutResponseWebObject.redirection = response.getRedirectURL().get()
            }
            checkoutResponseWebObject.paymentData = response.getData()

            return new WebView().data([checkout: checkoutResponseWebObject] as Map<String, Object>)

        } catch (final Exception e) {
            this.logger.error("Exception checking out", e);
            Map<String, Object> data = new HashMap<>();
            data.put("exception", ["message": e.getMessage()]);
            return new WebView().data(data);
        }
    }
}
