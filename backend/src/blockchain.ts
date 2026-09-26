export type BlockchainNetwork = "kaal-mainnet" | "kaal-testnet";

export type WithdrawalRequest = {
  withdrawalId: string;
  userId: string;
  address: string;
  network: BlockchainNetwork;
  amount: string;
};

export type BroadcastResult = {
  txHash: string;
  status: "submitted";
};

export interface BlockchainProvider {
  validateAddress(address: string, network: BlockchainNetwork): Promise<boolean>;
  broadcast(request: WithdrawalRequest): Promise<BroadcastResult>;
  getTransactionStatus(txHash: string, network: BlockchainNetwork): Promise<"submitted" | "confirmed" | "failed">;
}

/**
 * Safe Phase 7 placeholder.
 * It deliberately does not send funds. A real provider must be implemented
 * for the selected chain and kept server-side with secrets outside Git.
 */
export class DisabledBlockchainProvider implements BlockchainProvider {
  async validateAddress(address: string): Promise<boolean> {
    return address.trim().length > 0;
  }

  async broadcast(): Promise<BroadcastResult> {
    throw new Error("Blockchain payout provider is not enabled");
  }

  async getTransactionStatus(): Promise<"submitted" | "confirmed" | "failed"> {
    throw new Error("Blockchain payout provider is not enabled");
  }
}
