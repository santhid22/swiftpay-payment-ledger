import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const acceptedCounter = new Counter("accepted_202_total");
const failedCounter = new Counter("failed_request_total");

// FIXED: Corrected "local-host" typo to "localhost"
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const TARGET_TPS = Number(__ENV.TARGET_TPS || 250);
const TOTAL_REQUESTS = Number(__ENV.TOTAL_REQUESTS || 1000000);
const DURATION_SECONDS = Math.ceil(TOTAL_REQUESTS / TARGET_TPS);

export const options = {
  // REMOVED: discardResponseBodies: true (This was erasing the r.status value)
  scenarios: {
    payment_constant_rate: {
      executor: "constant-arrival-rate",
      rate: TARGET_TPS,
      timeUnit: "1s",
      duration: `${DURATION_SECONDS}s`,
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 300),
      maxVUs: Number(__ENV.MAX_VUS || 600),
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
    accepted_202_total: [`count>=${Math.floor(TOTAL_REQUESTS * 0.98)}`],
  },
};

export default function () {
  const txId = `k6-${uuidv4()}`;
  const senderSuffix = __VU % 1000;
  const receiverSuffix = (__VU + 500) % 1000;

  const payload = JSON.stringify({
    transactionId: txId,
    senderAccountId: `acct-sender-${senderSuffix}`,
    receiverAccountId: `acct-receiver-${receiverSuffix}`,
    amount: 10.25,
    currency: "USD",
  });

  const res = http.post(`${BASE_URL}/v1/payments`, payload, {
    headers: { "Content-Type": "application/json" },
    timeout: "30s",
  });

  // ADDED: Safety guard check to handle clean network drops gracefully without throwing exceptions
  if (!res) {
    failedCounter.add(1);
    return;
  }

  const ok = check(res, {
    "status is 202 accepted": (r) => r && r.status === 202,
  });

  if (ok) {
    acceptedCounter.add(1);
  } else {
    failedCounter.add(1);
  }

  sleep(0.01);
}
