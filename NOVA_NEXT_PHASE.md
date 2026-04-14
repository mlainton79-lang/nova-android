# Nova next phase

This build adds the local data foundation alongside the working chat system.

Included in this phase:
- Room dependencies and compiler setup
- NovaDatabase singleton
- ChatSessionEntity, ChatMessageEntity, MemoryItemEntity
- ChatDao and MemoryDao
- ChatRepositoryImpl and MemoryRepositoryImpl
- NovaViewModel and NovaUiState scaffolding

Important:
- The current working chat behaviour is intentionally left in place
- MainActivity is not yet switched over to Room
- This keeps the app buildable while the clean data layer is added safely

Recommended next step after this build works:
1. Verify the app still builds and runs
2. Add a small shadow-write bridge from ChatHistoryStore into ChatRepositoryImpl
3. Move chat rendering onto NovaViewModel
4. Then replace the fake live mode with the backend
