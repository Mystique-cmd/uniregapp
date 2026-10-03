package payment;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Coordinates an M-PESA STK Push without treating the Pay button or the
 * initial STK Push acknowledgement as proof of payment.
 *
 * The gateway must use a trusted server for Daraja credentials and deliver a
 * verified final result. The store must write every record to both Firebase
 * and the offline database, and call back successfully only after both writes
 * have completed. Registration is notified only after a successful payment
 * record has been saved through that store.
 */
public final class MpesaManager {
	private final PaymentGateway gateway;
	private final PaymentStore store;
	private final Map<String, PaymentContext> payments = new HashMap<String, PaymentContext>();

	public MpesaManager(PaymentGateway gateway, PaymentStore store) {
		if (gateway == null || store == null) {
			throw new IllegalArgumentException("Gateway and payment store are required");
		}
		this.gateway = gateway;
		this.store = store;
	}

	/** Starts payment collection; registration remains pending at this point. */
	public PaymentRecord payFees(String studentId, String phoneNumber,
								 BigDecimal amount, PaymentListener listener) {
		if (studentId == null || studentId.trim().isEmpty()) {
			throw new IllegalArgumentException("Student ID is required");
		}
		if (listener == null) {
			throw new IllegalArgumentException("Payment listener is required");
		}
		String phone = normalizeKenyanPhoneNumber(phoneNumber);
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
				|| amount.stripTrailingZeros().scale() > 0) {
			throw new IllegalArgumentException("Amount must be a positive whole number of KES");
		}

		String paymentId = UUID.randomUUID().toString();
		PaymentRecord pending = new PaymentRecord(paymentId, studentId.trim(), phone,
				amount.stripTrailingZeros(), Status.PENDING, null, null,
				"Payment request created", 1L, System.currentTimeMillis());
		final PaymentContext context = new PaymentContext(pending, listener);
		synchronized (payments) {
			payments.put(paymentId, context);
		}

		enqueue(context, pending, new PersistCallback() {
			@Override
			public void onSaved(PaymentRecord record) {
				try {
					gateway.requestStkPush(new StkPushRequest(record.getPaymentId(),
							record.getStudentId(), record.getPhoneNumber(), record.getAmount()),
							new GatewayCallback() {
								@Override
								public void onAccepted(String checkoutRequestId, String message) {
									handleAccepted(context, checkoutRequestId, message);
								}

								@Override
								public void onCompleted(PaymentResult result) {
									handleResult(context, result);
								}

								@Override
								public void onRequestFailed(String message, Throwable cause) {
									fail(context, message, cause);
								}
							});
				} catch (RuntimeException exception) {
					fail(context, "Unable to start M-PESA payment", exception);
				}
			}

			@Override
			public void onFailed(PaymentRecord record, Throwable cause) {
				notifyError(context, record, "Saving pending payment", cause);
			}
		});
		return pending;
	}

	/**
	 * Allows a trusted backend/polling adapter to deliver the verified final
	 * Daraja result. Never call this with a result supplied by the client UI.
	 */
	public void onMpesaResult(String paymentId, PaymentResult result) {
		PaymentContext context = findContext(paymentId);
		if (context == null) {
			throw new IllegalArgumentException("Unknown payment ID");
		}
		handleResult(context, result);
	}

	private void handleAccepted(final PaymentContext context, String checkoutRequestId,
								String message) {
		if (checkoutRequestId == null || checkoutRequestId.trim().isEmpty()) {
			fail(context, "M-PESA did not return a checkout request reference", null);
			return;
		}
		boolean start;
		synchronized (context) {
			PaymentRecord current = context.latest;
			if (current.getStatus() != Status.PENDING || context.terminalQueued) {
				return;
			}
			PaymentRecord processing = current.with(Status.PROCESSING,
					checkoutRequestId.trim(), current.getTransactionReference(),
					messageOrDefault(message, "M-PESA prompt sent; awaiting payment result"));
			start = queueLocked(context, processing, statusCallback(context));
		}
		if (start) {
			writeNext(context);
		}
	}

	private void handleResult(final PaymentContext context, PaymentResult result) {
		if (result == null) {
			notifyError(context, context.getLatest(), "Reading M-PESA response",
					new IllegalArgumentException("Payment result is missing"));
			return;
		}
		PaymentRecord current;
		Throwable validationError = null;
		boolean start = false;
		synchronized (context) {
			current = context.latest;
			if (context.terminalSaved || context.terminalQueued) {
				return;
			}
			if (result.isSuccessful()) {
				String reference = trimToNull(result.getTransactionReference());
				if (reference == null) {
					validationError = new IllegalArgumentException(
							"Successful response has no transaction reference");
				} else {
					PaymentRecord successful = current.with(Status.SUCCESS,
							firstNonEmpty(result.getCheckoutRequestId(), current.getCheckoutRequestId()),
							reference, messageOrDefault(result.getMessage(), "Payment confirmed"));
					context.terminalQueued = true;
					start = queueLocked(context, successful, terminalCallback(context, true));
				}
			} else {
				PaymentRecord failed = current.with(Status.FAILED,
						firstNonEmpty(result.getCheckoutRequestId(), current.getCheckoutRequestId()),
						trimToNull(result.getTransactionReference()),
						messageOrDefault(result.getMessage(), "M-PESA payment failed"));
				context.terminalQueued = true;
				start = queueLocked(context, failed, terminalCallback(context, false));
			}
		}
		if (validationError != null) {
			notifyError(context, current, "Verifying M-PESA response", validationError);
		} else if (start) {
			writeNext(context);
		}
	}

	private void fail(PaymentContext context, String message, Throwable cause) {
		boolean start;
		synchronized (context) {
			PaymentRecord current = context.latest;
			if (current.getStatus() == Status.SUCCESS || current.getStatus() == Status.FAILED
					|| context.terminalQueued || context.terminalSaved) {
				return;
			}
			PaymentRecord failed = current.with(Status.FAILED, current.getCheckoutRequestId(),
					current.getTransactionReference(), messageOrDefault(message, "M-PESA request failed"));
			context.terminalQueued = true;
			start = queueLocked(context, failed, terminalCallback(context, false, cause));
		}
		if (start) {
			writeNext(context);
		}
	}

	private PersistCallback statusCallback(final PaymentContext context) {
		return new PersistCallback() {
			@Override
			public void onSaved(PaymentRecord record) {
				try {
					context.listener.onStatusChanged(record);
				} catch (RuntimeException exception) {
					notifyError(context, record, "Notifying payment screen", exception);
				}
			}

			@Override
			public void onFailed(PaymentRecord record, Throwable cause) {
				notifyError(context, record, "Saving payment status", cause);
			}
		};
	}

	private PersistCallback terminalCallback(final PaymentContext context, final boolean successful) {
		return terminalCallback(context, successful, null);
	}

	private PersistCallback terminalCallback(final PaymentContext context, final boolean successful,
											final Throwable requestError) {
		return new PersistCallback() {
			@Override
			public void onSaved(PaymentRecord record) {
				if (successful) {
					try {
						context.listener.onPaymentConfirmed(record);
					} catch (RuntimeException exception) {
						notifyError(context, record, "Notifying registration module", exception);
					}
				} else {
					try {
						context.listener.onPaymentFailed(record);
					} catch (RuntimeException exception) {
						notifyError(context, record, "Notifying payment failure", exception);
					}
					if (requestError != null) {
						notifyError(context, record, "Starting M-PESA request", requestError);
					}
				}
			}

			@Override
			public void onFailed(PaymentRecord record, Throwable cause) {
				notifyError(context, record, "Saving final payment status", cause);
				if (requestError != null) {
					notifyError(context, record, "Starting M-PESA request", requestError);
				}
			}
		};
	}

	/** Serializes Firebase/offline writes so an older PROCESSING write cannot overwrite SUCCESS. */
	private void enqueue(final PaymentContext context, PaymentRecord record, PersistCallback callback) {
		boolean start;
		synchronized (context) {
			start = queueLocked(context, record, callback);
		}
		if (start) {
			writeNext(context);
		}
	}

	/** Must be called while holding the context monitor. */
	private boolean queueLocked(PaymentContext context, PaymentRecord record, PersistCallback callback) {
		context.latest = record;
		context.writes.add(new Write(record, callback));
		if (context.writing) {
			return false;
		}
		context.writing = true;
		return true;
	}

	private void writeNext(final PaymentContext context) {
		final Write write;
		synchronized (context) {
			write = context.writes.peek();
			if (write == null) {
				context.writing = false;
				return;
			}
		}
		try {
			store.saveToFirebaseAndOfflineDatabase(write.record, new StoreCallback() {
				@Override
				public void onComplete(Throwable error) {
					synchronized (context) {
						context.writes.remove();
						if (write.record.getStatus() == Status.SUCCESS
								|| write.record.getStatus() == Status.FAILED) {
							context.terminalSaved = error == null;
							context.terminalQueued = error == null;
						}
					}
					if (error == null) {
						write.callback.onSaved(write.record);
					} else {
						write.callback.onFailed(write.record, error);
					}
					writeNext(context);
				}
			});
		} catch (RuntimeException exception) {
			synchronized (context) {
				context.writes.remove();
				if (write.record.getStatus() == Status.SUCCESS
						|| write.record.getStatus() == Status.FAILED) {
					context.terminalSaved = false;
					context.terminalQueued = false;
				}
			}
			write.callback.onFailed(write.record, exception);
			writeNext(context);
		}
	}

	private PaymentContext findContext(String paymentId) {
		synchronized (payments) {
			return payments.get(paymentId);
		}
	}

	private void notifyError(PaymentContext context, PaymentRecord record, String stage,
							 Throwable cause) {
		try {
			context.listener.onError(record, stage, cause);
		} catch (RuntimeException ignored) {
			// A UI/listener exception must not interrupt payment persistence.
		}
	}

	private static String normalizeKenyanPhoneNumber(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Phone number is required");
		}
		String phone = value.replaceAll("[\\s()\\-]", "");
		if (phone.startsWith("+254")) {
			phone = phone.substring(1);
		} else if (phone.startsWith("0") && phone.length() == 10) {
			phone = "254" + phone.substring(1);
		}
		if (!phone.matches("254[17][0-9]{8}")) {
			throw new IllegalArgumentException("Enter a valid Kenyan M-PESA number");
		}
		return phone;
	}

	private static String trimToNull(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return value.trim();
	}

	private static String firstNonEmpty(String preferred, String fallback) {
		String value = trimToNull(preferred);
		return value == null ? fallback : value;
	}

	private static String messageOrDefault(String message, String fallback) {
		String value = trimToNull(message);
		return value == null ? fallback : value;
	}

	public enum Status {
		PENDING,
		PROCESSING,
		SUCCESS,
		FAILED
	}

	public interface PaymentGateway {
		/** Calls a trusted backend that initiates Daraja STK Push. */
		void requestStkPush(StkPushRequest request, GatewayCallback callback);
	}

	public interface GatewayCallback {
		/** Acceptance only means the M-PESA prompt was queued, not paid. */
		void onAccepted(String checkoutRequestId, String message);

		/** Called only with the verified final result from the backend/Daraja callback. */
		void onCompleted(PaymentResult result);

		void onRequestFailed(String message, Throwable cause);
	}

	public interface PaymentStore {
		/**
		 * Persist to Firebase and the offline database. Complete successfully
		 * only after both stores have been updated.
		 */
		void saveToFirebaseAndOfflineDatabase(PaymentRecord record, StoreCallback callback);
	}

	public interface StoreCallback {
		/** Pass null only when both Firebase and offline persistence succeeded. */
		void onComplete(Throwable error);
	}

	public interface PaymentListener {
		void onStatusChanged(PaymentRecord record);

		/** This is the sole success signal for the registration module. */
		void onPaymentConfirmed(PaymentRecord record);

		/** Registration must remain pending for this outcome. */
		void onPaymentFailed(PaymentRecord record);

		void onError(PaymentRecord record, String stage, Throwable cause);
	}

	public static final class StkPushRequest {
		private final String paymentId;
		private final String studentId;
		private final String phoneNumber;
		private final BigDecimal amount;

		private StkPushRequest(String paymentId, String studentId, String phoneNumber,
							   BigDecimal amount) {
			this.paymentId = paymentId;
			this.studentId = studentId;
			this.phoneNumber = phoneNumber;
			this.amount = amount;
		}

		public String getPaymentId() { return paymentId; }
		public String getStudentId() { return studentId; }
		public String getPhoneNumber() { return phoneNumber; }
		public BigDecimal getAmount() { return amount; }
	}

	public static final class PaymentResult {
		private final boolean successful;
		private final String transactionReference;
		private final String checkoutRequestId;
		private final String message;

		public PaymentResult(boolean successful, String transactionReference,
							 String checkoutRequestId, String message) {
			this.successful = successful;
			this.transactionReference = transactionReference;
			this.checkoutRequestId = checkoutRequestId;
			this.message = message;
		}

		public boolean isSuccessful() { return successful; }
		public String getTransactionReference() { return transactionReference; }
		public String getCheckoutRequestId() { return checkoutRequestId; }
		public String getMessage() { return message; }
	}

	public static final class PaymentRecord {
		private final String paymentId;
		private final String studentId;
		private final String phoneNumber;
		private final BigDecimal amount;
		private final Status status;
		private final String checkoutRequestId;
		private final String transactionReference;
		private final String message;
		private final long revision;
		private final long updatedAtMillis;

		private PaymentRecord(String paymentId, String studentId, String phoneNumber,
							  BigDecimal amount, Status status, String checkoutRequestId,
							  String transactionReference, String message, long revision,
							  long updatedAtMillis) {
			this.paymentId = paymentId;
			this.studentId = studentId;
			this.phoneNumber = phoneNumber;
			this.amount = amount;
			this.status = status;
			this.checkoutRequestId = checkoutRequestId;
			this.transactionReference = transactionReference;
			this.message = message;
			this.revision = revision;
			this.updatedAtMillis = updatedAtMillis;
		}

		private PaymentRecord with(Status nextStatus, String checkoutId, String transactionId,
								   String nextMessage) {
			return new PaymentRecord(paymentId, studentId, phoneNumber, amount, nextStatus,
					checkoutId, transactionId, nextMessage, revision + 1L,
					System.currentTimeMillis());
		}

		public String getPaymentId() { return paymentId; }
		public String getStudentId() { return studentId; }
		public String getPhoneNumber() { return phoneNumber; }
		public BigDecimal getAmount() { return amount; }
		public Status getStatus() { return status; }
		public String getCheckoutRequestId() { return checkoutRequestId; }
		public String getTransactionReference() { return transactionReference; }
		public String getMessage() { return message; }
		public long getRevision() { return revision; }
		public long getUpdatedAtMillis() { return updatedAtMillis; }
	}

	private interface PersistCallback {
		void onSaved(PaymentRecord record);
		void onFailed(PaymentRecord record, Throwable cause);
	}

	private static final class Write {
		private final PaymentRecord record;
		private final PersistCallback callback;

		private Write(PaymentRecord record, PersistCallback callback) {
			this.record = record;
			this.callback = callback;
		}
	}

	private static final class PaymentContext {
		private PaymentRecord latest;
		private final PaymentListener listener;
		private final ArrayDeque<Write> writes = new ArrayDeque<Write>();
		private boolean writing;
		private boolean terminalSaved;
		private boolean terminalQueued;

		private PaymentContext(PaymentRecord initial, PaymentListener listener) {
			this.latest = initial;
			this.listener = listener;
		}

		private synchronized PaymentRecord getLatest() {
			return latest;
		}

	}
}
