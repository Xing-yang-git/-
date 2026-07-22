/**
 * constants.js 单元测试。
 * 验证 STATUS 和 POST_TYPE 常量值与后端定义一致。
 */

describe('constants', () => {
  let constants;

  beforeEach(() => {
    // 每次重新 require 以获取干净模块
    jest.resetModules();
    constants = require('../constants');
  });

  // ==================== STATUS ====================

  describe('STATUS', () => {
    it('应包含全部通用流转状态', () => {
      expect(constants.STATUS.PENDING).toBe('pending');
      expect(constants.STATUS.APPROVED).toBe('approved');
      expect(constants.STATUS.REJECTED).toBe('rejected');
      expect(constants.STATUS.RETURNED).toBe('returned');
      expect(constants.STATUS.COMPLETED).toBe('completed');
    });

    it('应包含全部内容状态', () => {
      expect(constants.STATUS.ONLINE).toBe('online');
      expect(constants.STATUS.OFFLINE).toBe('offline');
    });

    it('应包含全部账号状态', () => {
      expect(constants.STATUS.REGISTERING).toBe('registering');
      expect(constants.STATUS.BANNED).toBe('banned');
      expect(constants.STATUS.CANCELLED).toBe('cancelled');
    });

    it('应包含全部借用交互状态', () => {
      expect(constants.STATUS.RESERVED).toBe('reserved');
      expect(constants.STATUS.BORROWING).toBe('borrowing');
    });

    it('所有常量值应为小写字符串', () => {
      Object.values(constants.STATUS).forEach(value => {
        expect(value).toBe(value.toLowerCase());
      });
    });
  });

  // ==================== POST_TYPE ====================

  describe('POST_TYPE', () => {
    it('应包含三种发布类型', () => {
      expect(constants.POST_TYPE.LEND).toBe('LEND');
      expect(constants.POST_TYPE.WANTED).toBe('WANTED');
      expect(constants.POST_TYPE.HELP).toBe('HELP');
    });

    it('POST_TYPE 值应为大写字符串', () => {
      Object.values(constants.POST_TYPE).forEach(value => {
        expect(value).toBe(value.toUpperCase());
      });
    });
  });
});
