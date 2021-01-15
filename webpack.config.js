module.exports = {
    mode: 'production',
    module: {
        rules: [
            {
                test: /\.css$/i,
                use: ["style-loader", "css-loader"],
            },
        ],
    },
    node: {
        fs: 'empty'
    },
    entry: './src/district_registry/shared/js/index.js',
    output: {
        filename: 'index_bundle.js'
    }
};
