module.exports = {
    mode: 'production',
    node: {
        fs: 'empty'
    },
    entry: './src/district_registry/shared/js/index.js',
    output: {
        filename: 'index_bundle.js'
    }
};
