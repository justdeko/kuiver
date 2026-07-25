// Large-graph tests exceed mocha's 2s default on slower CI machines
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 30000 });
