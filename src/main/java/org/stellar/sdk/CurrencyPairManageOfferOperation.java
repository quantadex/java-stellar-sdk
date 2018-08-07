package org.stellar.sdk;

import org.stellar.sdk.xdr.Int64;
import org.stellar.sdk.xdr.ManageOfferOp;
import org.stellar.sdk.xdr.OperationType;
import org.stellar.sdk.xdr.Uint64;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents <a href="https://www.stellar.org/developers/learn/concepts/list-of-operations.html#manage-offer" target="_blank">ManageOffer</a> operation.
 *
 * @see <a href="https://www.stellar.org/developers/learn/concepts/list-of-operations.html" target="_blank">List of Operations</a>
 */
public class CurrencyPairManageOfferOperation extends Operation {
    // implementation notes:
    //   This is basically a copy of ManageOfferOperation which, unfortunately, only has a private constructor.
    //   Based on https://github.com/stellar/java-stellar-sdk/blob/a4961e3b7fb79755cc39e741252ca30c84505742/src/main/java/org/stellar/sdk/ManageOfferOperation.java

    private static final int SCALE = 7;
    private static final BigDecimal PRECISION = BigDecimal.valueOf(10000000L);
    private static final BigDecimal ONE = BigDecimal.valueOf(1);

    private final long amount;
    private final Asset buying;
    private final long offerId;
    private final Price price;
    private final Asset selling;

    /**
     * Note: amount and price should be adjusted (i.e. multiplied) by PRECISION.
     *
     * @param selling
     * @param buying
     * @param amount
     * @param price
     * @param offerId
     */
    private CurrencyPairManageOfferOperation(Asset selling, Asset buying, long amount, Price price, long offerId) {
        this.selling = checkNotNull(selling, "selling cannot be null");
        this.buying = checkNotNull(buying, "buying cannot be null");
        this.amount = amount;
        this.price = checkNotNull(price, "price cannot be null");
        // offerId can be null
        this.offerId = offerId;
    }

    /**
     * The asset being sold in this operation
     */
    public Asset getSelling() {
        return selling;
    }

    /**
     * The asset being bought in this operation
     */
    public Asset getBuying() {
        return buying;
    }

    /**
     * Amount of selling being sold.
     */
    public long getAmount() {
        return amount;
    }

    /**
     * Price of 1 unit of selling in terms of buying.
     */
    public Price getPrice() {
        return price;
    }

    /**
     * The ID of the offer.
     */
    public long getOfferId() {
        return offerId;
    }

    @Override
    org.stellar.sdk.xdr.Operation.OperationBody toOperationBody() {
        ManageOfferOp op = new ManageOfferOp();
        op.setSelling(selling.toXdr());
        op.setBuying(buying.toXdr());
        Int64 amount = new Int64();
        amount.setInt64(this.amount);
        op.setAmount(amount);
        op.setPrice(price.toXdr());
        Uint64 offerId = new Uint64();
        offerId.setUint64(Long.valueOf(this.offerId));
        op.setOfferID(offerId);

        org.stellar.sdk.xdr.Operation.OperationBody body = new org.stellar.sdk.xdr.Operation.OperationBody();
        body.setDiscriminant(OperationType.MANAGE_OFFER);
        body.setManageOfferOp(op);

        return body;
    }

    /**
     * Builder class for {@link CurrencyPairManageOfferOperation} whose interface is convenient
     * when the user would like to keep their counter currency (usually their local fiat currency) constant.
     *
     * Caveat: this will approximate decimals as fractions, unexpected precision errors may occur
     *
     * @see {@link CurrencyPairManageOfferOperation}
     */
    public static class Builder {
        private final Asset base;
        private final Asset counter;
        private final String amount;
        private final String price;
        private long offerId = 0;
        private boolean _buyBase = true;
        private KeyPair mSourceAccount;

        /**
         * Creates a new {@link CurrencyPairManageOfferOperation.Builder}.
         *
         * Constructor defaults to buying the base. Call {@link #buyCounter()} or {@link #goShort} if this is not true.
         *
         * @param base
         * @param counter
         * @param amount Amount being exchanged.
         * @param price Price of 1 unit of selling in terms of buying.
         * @throws ArithmeticException when amount has more than 7 decimal places.
         */
        public Builder(Asset base, Asset counter, String amount, String price) {
            this.base = checkNotNull(base, "base cannot be null");
            this.counter = checkNotNull(counter, "counter cannot be null");
            this.amount = checkNotNull(amount, "amount cannot be null");
            this.price = checkNotNull(price, "price cannot be null");
        }

        /** Trader intends to go long on this currency pair, i.e. sell the counter currency and buy the base */
        public Builder goLong() {
            this._buyBase = true;
            return this;
        }

        /** Synonymous with {@link #goLong()} */
        public Builder sellCounter() {
            return goLong();
        }

        /** Synonymous with {@link #goLong()} */
        public Builder buyBase() {
            return goLong();
        }

        /** Trader intends to short this currency pair, i.e. buy the counter currency and sell the base */
        public Builder goShort() {
            this._buyBase = false;
            return this;
        }

        /** Synonymous with {@link #goShort()} */
        public Builder sellBase() {
            return goShort();
        }

        /** Synonymous with {@link #goShort()} */
        public Builder buyCounter() {
            return goShort();
        }

        public boolean isBuyingBase() {
            return this._buyBase;
        }

        public boolean isBuyingCounter() {
            return !this._buyBase;
        }

        public Asset getBuyingAsset() {
            return _buyBase ? base : counter;
        }

        public Asset getSellingAsset() {
            return _buyBase ? counter : base;
        }

        /**
         * Sets offer ID. <code>0</code> creates a new offer. Set to existing offer ID to change it.
         * @param offerId
         */
        public Builder setOfferId(long offerId) {
            this.offerId = offerId;
            return this;
        }

        /**
         * Sets the source account for this operation.
         * @param sourceAccount The operation's source account.
         * @return Builder object so you can chain methods.
         */
        public Builder setSourceAccount(KeyPair sourceAccount) {
            mSourceAccount = checkNotNull(sourceAccount, "sourceAccount cannot be null");
            return this;
        }

        /**
         * Builds an operation.
         */
        public CurrencyPairManageOfferOperation build() {
            final Asset buying;
            final Asset selling;
            final long buyingAmount;
            final Price buyingPrice;

            final BigDecimal dPrice = new BigDecimal(price);

            if (_buyBase) {
                // tricky math when buying the base
                buying = base;
                selling = counter;

                final BigDecimal dAmount = new BigDecimal(amount);
                BigDecimal t;

                t = dAmount.multiply(dPrice).multiply(PRECISION).setScale(0, RoundingMode.HALF_EVEN);
                buyingAmount = t.longValueExact();

                t = ONE.divide(dPrice, MathContext.DECIMAL64);
                buyingPrice = Price.fromString(t.toPlainString());
            } else {
                // no math needed when buying the counter
                buying = counter;
                selling = base;

                buyingAmount = (new BigDecimal(amount)).multiply(PRECISION).longValueExact();
                buyingPrice = Price.fromString(price);
            }

            CurrencyPairManageOfferOperation operation = new CurrencyPairManageOfferOperation(selling, buying, buyingAmount, buyingPrice, offerId);
            if (mSourceAccount != null) {
                operation.setSourceAccount(mSourceAccount);
            }
            return operation;
        }
    }
}
