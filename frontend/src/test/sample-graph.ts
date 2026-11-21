import type { DependencyGraph } from "@/lib/api";

/** A small graph: workspaces and categories first, documents need both, members need workspaces. */
export const sampleGraph: DependencyGraph = {
  resources: [
    {
      key: "/categories",
      name: "categories",
      idParam: "categoryId",
      operations: { LIST: "listCategories", READ: "getCategory" },
      actions: [],
      idSource: { kind: "LIST_RESPONSE", operationId: "listCategories", pointer: "/0/id", confidence: 0.8 },
    },
    {
      key: "/workspaces",
      name: "workspaces",
      idParam: "workspaceId",
      operations: { CREATE: "createWorkspace", READ: "getWorkspace", DELETE: "deleteWorkspace" },
      actions: [],
      idSource: { kind: "CREATE_RESPONSE", operationId: "createWorkspace", pointer: "/id", confidence: 0.85 },
    },
    {
      key: "/workspaces/{workspaceId}/members",
      name: "members",
      idParam: "memberId",
      operations: { CREATE: "addMember", DELETE: "removeMember" },
      actions: [],
      idSource: { kind: "CREATE_RESPONSE", operationId: "addMember", pointer: "/id", confidence: 0.85 },
    },
    {
      key: "/documents",
      name: "documents",
      idParam: "documentId",
      operations: { CREATE: "createDocument", DELETE: "deleteDocument" },
      actions: ["publish"],
      idSource: { kind: "CREATE_RESPONSE", operationId: "createDocument", pointer: "/id", confidence: 0.85 },
    },
  ],
  dependencies: [
    {
      from: "/workspaces/{workspaceId}/members",
      to: "/workspaces",
      input: "path:workspaceId",
      required: true,
      confidence: 1,
      reason: "The path of members contains {workspaceId}, which identifies one item of workspaces",
    },
    {
      from: "/documents",
      to: "/workspaces",
      input: "body:/workspaceId",
      required: true,
      confidence: 0.9,
      reason: "Field workspaceId has the same name as the ID parameter of workspaces",
    },
    {
      from: "/documents",
      to: "/categories",
      input: "body:/categoryId",
      required: false,
      confidence: 0.5,
      reason: "Field categoryId ends with the resource name categories",
    },
  ],
  droppedForCycles: [],
  levels: [
    ["/categories", "/workspaces"],
    ["/workspaces/{workspaceId}/members", "/documents"],
  ],
  standaloneOperations: ["token", "register"],
  auth: [
    {
      scheme: "bearerAuth",
      tokenOperationId: "token",
      tokenPointer: "/accessToken",
      registerOperationId: "register",
      valueTemplate: "Bearer {{token}}",
    },
  ],
  unresolved: [],
};
