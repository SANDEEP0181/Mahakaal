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
 * Phase 9 provider contract.
 * No private key or RPC secret is embedded in source.
 */
export class DisabledBlockchainProvider implements BlockchainProvider {
  async validateAddress(address: string): Promise<boolean> {
    return /^0x[a-fA-F0-9]{40}$/.test(address.trim());
  }

  async broadcast(): Promise<BroadcastResult> {
    throw new Error("Blockchain payout provider is not enabled");
  }

  async getTransactionStatus(): Promise<"submitted" | "confirmed" | "failed"> {
    throw new Error("Blockchain payout provider is not enabled");
  }
}

export function getBlockchainProvider(): BlockchainProvider {
  return new DisabledBlockchainProvider();
}
